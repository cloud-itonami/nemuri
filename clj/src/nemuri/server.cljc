(ns nemuri.server
  (:gen-class)
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [jsonista.core :as json]
            [nemuri.core :as core]
            [nemuri.registry :as registry])
  (:import [com.sun.net.httpserver HttpExchange HttpHandler HttpServer]
           [java.io ByteArrayOutputStream]
           [java.net InetSocketAddress URLDecoder]
           [java.nio.charset StandardCharsets]))

(def mapper (json/object-mapper {:decode-key-fn keyword :encode-key-fn name}))

(defn- read-body [^HttpExchange exchange]
  (with-open [in (.getRequestBody exchange)
              out (ByteArrayOutputStream.)]
    (.transferTo in out)
    (.toString out StandardCharsets/UTF_8)))

(defn- write-json! [^HttpExchange exchange status body]
  (let [bytes (.getBytes (json/write-value-as-string body mapper) StandardCharsets/UTF_8)]
    (.set (.getResponseHeaders exchange) "content-type" "application/json; charset=utf-8")
    (.sendResponseHeaders exchange status (count bytes))
    (with-open [out (.getResponseBody exchange)]
      (.write out bytes))))

(defn- request-json [exchange]
  (let [body (read-body exchange)]
    (if (seq body) (json/read-value body mapper) {})))

(defn- camel->snake [s]
  (-> (name s)
      (str/replace #"([a-z0-9])([A-Z])" "$1_$2")
      str/lower-case))

(defn- normalize-keys [x]
  (cond
    (map? x) (into {} (map (fn [[k v]] [(keyword (camel->snake k)) (normalize-keys v)]) x))
    (vector? x) (mapv normalize-keys x)
    :else x))

(defn handle-runs [exchange]
  (let [request (request-json exchange)
        graph (or (:assistant_id request) (:assistantId request) (:graph request))
        result (registry/dispatch graph (or (:input request) (:payload request) {}))
        status (if (= "unknown_graph" (:error result)) 404 200)]
    (write-json! exchange status result)))

(defn handle-xrpc [exchange nsid]
  (let [payload (normalize-keys (request-json exchange))
        result (registry/dispatch-nsid nsid payload)
        status (if (:error result) 404 200)]
    (write-json! exchange status result)))

(defn route [^HttpExchange exchange]
  (try
    (let [method (.getRequestMethod exchange)
          path (.getPath (.getRequestURI exchange))]
      (cond
        (and (= "GET" method) (#{"ok" "health"} (subs path 1)))
        (write-json! exchange 200 (core/health {}))

        (and (= "POST" method) (= "/runs" path))
        (handle-runs exchange)

        (and (= "POST" method) (str/starts-with? path "/xrpc/"))
        (handle-xrpc exchange (URLDecoder/decode (subs path (count "/xrpc/")) "UTF-8"))

        :else
        (write-json! exchange 404 {:error "not_found"})))
    (catch Exception ex
      (log/error ex "nemuri request failed")
      (write-json! exchange 500 {:error "internal_error" :message (.getMessage ex)}))))

(defn create-server
  ([] (create-server (Integer/parseInt (or (System/getenv "LANGSERVER_PORT")
                                           (System/getenv "PORT")
                                           "8000"))))
  ([port]
   (let [server (HttpServer/create (InetSocketAddress. port) 0)]
     (.createContext server "/" (reify HttpHandler
                                  (handle [_ exchange] (route exchange))))
     (.setExecutor server nil)
     server)))

(defn -main [& _args]
  (let [server (create-server)]
    (.start server)
    (log/info "nemuri CLJ server listening" (.getAddress server))
    @(promise)))

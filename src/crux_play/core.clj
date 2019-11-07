(ns crux-play.core
  (:require [crux.api :as crux])
  (:import [crux.api ICruxAPI]))

(defn start-standalone-node
  ^crux.api.ICruxAPI
  [rocks?]
  ;; Even in standalone, you get persistent data if you use RocksKV.
  (crux/start-standalone-node {:kv-backend (if rocks? "crux.kv.rocksdb.RocksKv" "crux.kv.memdb.MemKv")
                               :db-dir "data/db-dir-standalone"
                               :event-log-dir "data/eventlog-standalone"}))

;; Note: Crux Kafka stuff deps on java 10 (`crux/start-standalone-node` is fine with java 8)
(defn start-cluster-node
  ^crux.api.ICruxAPI
  [rocks?]
  (crux/start-cluster-node {:bootstrap-servers "localhost:9092"
                            :kv-backend (if rocks? "crux.kv.rocksdb.RocksKv" "crux.kv.memdb.MemKv")
                            :db-dir "data/db"
                            :event-log-dir "data/event-log"
                            :tx-topic "tx-1" ; choose your tx-topic name
                            :doc-topic "doc-1" ; choose your doc-topic name
                            }))

(defn easy-ingest
  "Uses Crux put transaction to add a vector of documents to a specified
  node"
  [node docs]
  (crux/submit-tx node
                  (vec (for [doc docs]
                         [:crux.tx/put doc]))))

(defn easy-ingest-sync
  [node docs]
  (let [{:crux.tx/keys [tx-time] :as tx} (easy-ingest node docs)]
    (crux/sync node tx-time nil)
    tx))

(defn full-query
  "Get _everything_; handy for dev troubleshooting."
  [node]
  (crux/q
   (crux/db node)
   '{:find [id]
     :where [[e :crux.db/id id]]
     :full-results? true}))

(comment
  ;; Pick your flavor...
  (def node (start-standalone-node false))
  (def node (start-standalone-node true))
  (def node (start-cluster-node false))
  ;; ideally what we'd use for prod
  (def node (start-cluster-node true))

  (easy-ingest-sync node
                    [{:crux.db/id :demo
                      :msg "Hi there!"}])

  (full-query node)

  ;; Be sure to close node when done with it...
  (.close node)

  (easy-ingest-sync node
                    [{:crux.db/id :people/sam
                      :first-name "Sam"
                      :last-name "Brauer"}
                     {:crux.db/id :people/casp
                      :first-name "Eric"
                      :last-name "Caspary"}])

  (easy-ingest-sync node
                    [{:crux.db/id :people/robert
                      :first-name "Robert"
                      :last-name "Pierce"}])

  (crux/q (crux/db node)
          {:find '[fname lname]
           :where '[[e :crux.db/id eid]
                    [e :first-name fname]
                    [e :last-name lname]]
           :args [{'eid :people/sam}]})
  )

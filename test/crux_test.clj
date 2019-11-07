(ns crux-test
  (:require [clojure.test :refer :all]
            [crux.api :as crux])
  (:import [crux.api ICruxAPI]))

(defn start-node
  ^crux.api.ICruxAPI
  [uuid rocks?]
  (let [config {:crux.node/topology :crux.kafka/topology
                :crux.kafka/bootstrap-servers "localhost:9092"
                :crux.kafka/tx-topic (str "crux-tx-" uuid)
                :crux.kafka/doc-topic (str "crux-doc-" uuid)
                :crux.node/kv-store (if rocks?
                                      "crux.kv.rocksdb/kv"
                                      "crux.kv.memdb/kv")
                :crux.kv/db-dir (str "data/db-" uuid)}]
    (clojure.pprint/pprint config)
    (crux/start-node config)))

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

(defn entity
  [node eid]
  (crux/entity (crux/db node) eid))

(defn test-node
  [rocks?]
  (let [uuid (java.util.UUID/randomUUID)
        eid :foo
        doc {:crux.db/id eid :stuff 123}]
    (println "uuid:" uuid)

    (testing "can read what i just wrote"
      (with-open [node (start-node uuid rocks?)]
        (easy-ingest-sync node [doc])
        (is (= doc (entity node eid)))))

    (testing "can read what i wrote earlier"
      (with-open [node (start-node uuid rocks?)]
        (is (= doc (entity node eid)))))))

(deftest test-rocks-node
  (test-node true))

(deftest test-lmdb-node
  (test-node false))

;;
;; When I run these tests, everything passes except the "can read what i wrote earlier" assertion for LMDB.
;; That's surprising to me, since outside of the test I find that I can't read what I wrote earlier with RocksKV, while I can with LMDB. Wtf?
;; See repl session in comment at end of file...
;;

(defn full-query
  "Get _everything_; handy for dev troubleshooting."
  [node]
  (crux/q
   (crux/db node)
   '{:find [id]
     :where [[e :crux.db/id id]]
     :full-results? true}))

(comment
  ;; Sample repl session where data seems to be missing when reconnecting to a node using Rocks
  ;; (whereas LMDB seems fine; the opposite of what I see running the tests)

crux-test> (def uuid1 (java.util.UUID/randomUUID))
#'crux-test/uuid1
crux-test> (def lnode (start-node uuid1 false))
{:crux.node/topology :crux.kafka/topology,
 :crux.kafka/bootstrap-servers "localhost:9092",
 :crux.kafka/tx-topic "crux-tx-8366f3ac-a263-4af0-887c-78cd2e1efb99",
 :crux.kafka/doc-topic "crux-doc-8366f3ac-a263-4af0-887c-78cd2e1efb99",
 :crux.node/kv-store "crux.kv.memdb/kv",
 :crux.kv/db-dir "data/db-8366f3ac-a263-4af0-887c-78cd2e1efb99"}
#'crux-test/lnode

crux-test> (full-query lnode)
#{}
crux-test> (easy-ingest-sync lnode [{:crux.db/id :foo :stuff 123}])
#:crux.tx{:tx-id 0, :tx-time #inst "2019-11-07T15:29:00.629-00:00"}
crux-test>
crux-test> (full-query lnode)
#{[{:crux.db/id :foo, :stuff 123}]}
crux-test> (. lnode close)
nil
crux-test>
crux-test>
crux-test> (def lnode (start-node uuid1 false))
{:crux.node/topology :crux.kafka/topology,
 :crux.kafka/bootstrap-servers "localhost:9092",
 :crux.kafka/tx-topic "crux-tx-8366f3ac-a263-4af0-887c-78cd2e1efb99",
 :crux.kafka/doc-topic "crux-doc-8366f3ac-a263-4af0-887c-78cd2e1efb99",
 :crux.node/kv-store "crux.kv.memdb/kv",
 :crux.kv/db-dir "data/db-8366f3ac-a263-4af0-887c-78cd2e1efb99"}
#'crux-test/lnode
crux-test>
crux-test>
crux-test> (full-query lnode)
#{[{:crux.db/id :foo, :stuff 123}]}
crux-test>
crux-test> (. lnode close)
nil
crux-test>
crux-test> (def uuid2 (java.util.UUID/randomUUID))
#'crux-test/uuid2
crux-test>
crux-test>
crux-test> (def rnode (start-node uuid2 true))
{:crux.node/topology :crux.kafka/topology,
 :crux.kafka/bootstrap-servers "localhost:9092",
 :crux.kafka/tx-topic "crux-tx-b9f5fb9c-3b5e-4c65-8c74-fc0dd97716af",
 :crux.kafka/doc-topic "crux-doc-b9f5fb9c-3b5e-4c65-8c74-fc0dd97716af",
 :crux.node/kv-store "crux.kv.rocksdb/kv",
 :crux.kv/db-dir "data/db-b9f5fb9c-3b5e-4c65-8c74-fc0dd97716af"}
#'crux-test/rnode
crux-test>
crux-test> (full-query rnode)
#{}
crux-test> (easy-ingest-sync rnode [{:crux.db/id :foo :stuff 123}])
#:crux.tx{:tx-id 0, :tx-time #inst "2019-11-07T15:31:13.953-00:00"}
crux-test>
crux-test>
crux-test> (full-query rnode)
#{[{:crux.db/id :foo, :stuff 123}]}
crux-test>
crux-test>
crux-test> (. rnode close)
nil
crux-test>
crux-test> (def rnode (start-node uuid2 true))
{:crux.node/topology :crux.kafka/topology,
 :crux.kafka/bootstrap-servers "localhost:9092",
 :crux.kafka/tx-topic "crux-tx-b9f5fb9c-3b5e-4c65-8c74-fc0dd97716af",
 :crux.kafka/doc-topic "crux-doc-b9f5fb9c-3b5e-4c65-8c74-fc0dd97716af",
 :crux.node/kv-store "crux.kv.rocksdb/kv",
 :crux.kv/db-dir "data/db-b9f5fb9c-3b5e-4c65-8c74-fc0dd97716af"}
#'crux-test/rnode
crux-test>
crux-test> (full-query rnode)
#{}  ;; I expected to see my document in the set, but instead I get an empty set.
crux-test> (. rnode close)
nil
crux-test>
)

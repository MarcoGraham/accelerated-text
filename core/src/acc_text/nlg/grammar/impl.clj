(ns acc-text.nlg.grammar.impl
  (:require [acc-text.nlg.graph.amr :refer [attach-amrs]]
            [acc-text.nlg.graph.condition :refer [determine-conditions]]
            [acc-text.nlg.graph.utils :refer [find-root-id get-successors get-in-edge add-concept-position prune-graph]]
            [acc-text.nlg.semantic-graph.utils :refer [semantic-graph->ubergraph]]
            [clojure.string :as str]
            [loom.alg :refer [pre-traverse]]
            [loom.attr :refer [attrs]]))

(def data-types #{:data :quote :dictionary-item})

(defn escape-string [s]
  (str/replace s #"\"" "\\\\\""))

(defn node->cat [graph node-id]
  (let [{:keys [type position]} (attrs graph node-id)]
    (str (->> (str/split (name type) #"-")
              (map str/capitalize)
              (str/join))
         (format "%02d" (or position 0)))))

(defn remove-data-types [graph node-ids]
  (remove #(contains? data-types (:type (attrs graph %))) node-ids))

(defmulti build-node (fn [graph node-id] (:type (attrs graph node-id))))

(defmethod build-node :default [graph node-id]
  (let [successors (get-successors graph node-id)
        cat (node->cat graph node-id)]
    {:cat    [cat]
     :fun    {cat (->> successors (remove-data-types graph) (map #(node->cat graph %)))}
     :lincat {cat "Text"}
     :lin    {cat (str/join " | " (map #(node->cat graph %) successors))}}))

(defmethod build-node :operation [graph node-id]
  (let [{:keys [name module]} (attrs graph node-id)
        successors (get-successors graph node-id)
        cat (node->cat graph node-id)]
    {:cat    [cat]
     :fun    {cat (->> successors (remove-data-types graph) (map #(node->cat graph %)))}
     :lincat {cat (or (:category (attrs graph (get-in-edge graph node-id))) "Text")}
     :lin    {cat (cond-> (str module "." name)
                          (seq successors) (str " " (str/join " " (map #(node->cat graph %) successors))))}}))

(defmethod build-node :quote [graph node-id]
  (let [cat (node->cat graph node-id)]
    {:oper   [[cat "Str" (format "\"%s\"" (escape-string (:value (attrs graph node-id))))]]}))

(defn ->graph [semantic-graph context]
  (-> semantic-graph
      (semantic-graph->ubergraph)
      (attach-amrs context)
      (determine-conditions context)
      (prune-graph)
      (add-concept-position)))

(defn build-grammar
  ([semantic-graph context]
   (build-grammar "Default" "Instance" semantic-graph context))
  ([module instance semantic-graph context]
   (let [graph (->graph semantic-graph context)
         start-id (find-root-id graph)]
     (reduce (fn [grammar node-id]
               (merge-with (fn [acc val]
                             (cond
                               (map? acc) (merge acc val)
                               (coll? acc) (concat acc val)))
                           grammar
                           (build-node graph node-id)))
             {:module   module
              :instance instance
              :flags    {:startcat (node->cat graph start-id)}
              :cat      []
              :fun      {}
              :lincat   {}
              :lin      {}
              :oper     []}
             (pre-traverse graph start-id)))))
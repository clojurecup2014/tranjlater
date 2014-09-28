(ns tranjlator.hashing
  (:refer-clojure :exclude [hash])
  (:import [goog.crypt Sha256]))

(def ^:const +hex-chars+ [\0 \1 \2 \3 \4 \5 \6 \7 \8 \9 \A \B \C \D \E \F])

(defn hex-string
  [bytes]
  (->> bytes
       (map #(bit-and (int %) 0xFF))
       (reduce (fn [acc unsigned]
                 (-> acc
                     (conj (get +hex-chars+ (bit-shift-right unsigned 4)))
                     (conj (get +hex-chars+ (bit-and unsigned 0x0F)))))
               [])
       (apply str)))

(defn hash
  [s]
  (.digest (doto (Sha256.)
             (.update s))))

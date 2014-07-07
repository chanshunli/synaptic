(ns
  ^{:doc "Various utility functions."
    :author "Antoine Choppin"}
  synaptic.util
  (:require [clatrix.core :as m])
  (:import [java.io DataInputStream File FileInputStream FileWriter]))


; Random numbers

(def rand-gen (java.util.Random.))

(defn rand-set!
  "Set the seed of the random number generator."
  [seed]
  (.setSeed rand-gen seed))

(defn nrand
  "Get a random sample of a normal distribution."
  []
  (.nextGaussian rand-gen))

(defn random
  "Get a random sample of a uniform distribution."
  []
  (.nextFloat rand-gen))

; Bit array manipulation

(defn pack
  "Pack a vector of 0 and 1 into a bit array."
  [dataarray]
  (loop [data dataarray
         mask 128
         acc  0
         bits []]
    (if (empty? data)
      (if (= 128 mask) bits (conj bits acc))
      (let [b (+ acc (* mask (first data)))]
        (if (= 1 mask)
          (recur (rest data) 128 0 (conj bits b))
          (recur (rest data) (quot mask 2) b bits))))))

(defn unpack
  "Unpack a bit array into a vector of 0 and 1."
  [bitarray]
  (loop [bits bitarray
         mask 128
         data []]
    (if (empty? bits)
      data
      (let [d (if (= 0 (bit-and mask (first bits))) 0 1)]
        (if (= 1 mask)
          (recur (rest bits) 128 (conj data d))
          (recur bits (quot mask 2) (conj data d)))))))

(defn imgint2bits
  "Converts a vector of 0 and 1 (representing a binary image of size w h)
  into an int vector binary representation of the image."
  [data w h]
  {:pre  [(= (count data) (* (int (Math/ceil (/ w 8))) h))]
   :post [(= (count %) (* w h))]}
  (let [linelen (int (* (Math/ceil (/ w 8)) 8))
        bits    (unpack data)]
    (if (> linelen w)
      (apply vector (apply concat (map (partial take w) (partition linelen bits))))
      bits)))

(defn imgbits2int
  "Converts an int vector binary representation of an image of size w h
  into a vector of 0 and 1."
  [bits w h]
  {:pre  [(= (count bits) (* w h))]
   :post [(= (count %) (* (int (Math/ceil (/ w 8))) h))]}
  (let [linelen (int (* (Math/ceil (/ w 8)) 8))]
    (if (> linelen w)
      (let [padding     (repeat (- 8 (inc (rem (dec w) 8))) 1)
            paddedlines (map #(concat % padding) (partition w bits))]
        (pack (apply concat paddedlines)))
      (pack bits))))

; Basic data manipulation

(defn unique
  "Returns a vector of unique values, sorted."
  [values]
  (vec (sort (into #{} values))))

; Base-64 encoding

(def ^:private enc-map
  "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/")

(def ^:private dec-map
  (zipmap (seq enc-map) (range (count enc-map))))

(defn tobase64
  "Encode an int vector into a Base64 string."
  [sample]
  (loop [ss sample, encoded ""]
    (let [[s1 s2 s3] (take 3 ss)]
      (if s1
        (let [c1  (bit-and 0x3f (bit-shift-right s1 2))
              c21 (bit-shift-left (bit-and 0x3 s1) 4)]
          (if s2
            (let [c22 (bit-and 0xf (bit-shift-right s2 4))
                  c31 (bit-shift-left (bit-and 0xf s2) 2)]
              (if s3
                (let [c32 (bit-and 0x3 (bit-shift-right s3 6))
                      c4  (bit-and 0x3f s3)]
                  (recur (nthrest ss 3)
                         (str encoded (apply str
                                             (map #(nth enc-map %)
                                                  [c1 (+ c21 c22) (+ c31 c32) c4])))))
                (str encoded (apply str (map #(nth enc-map %)
                                             [c1 (+ c21 c22) c31])) "=")))
            (str encoded (apply str (map #(nth enc-map %) [c1 c21])) "==")))
        encoded))))

(defn frombase64
  "Decode a Base64 string into an int vector."
  [encoded]
  (loop [enc encoded, decoded []]
    (if (empty? enc) decoded
      (let [[e1 e2 e3 e4] (map dec-map (take 4 enc))
            d11 (bit-shift-left e1 2)
            d12 (bit-and 0x3 (bit-shift-right e2 4))]
        (if (not= \= (nth enc 2))
          (let [d21 (bit-shift-left (bit-and 0xf e2) 4)
                d22 (bit-and 0xf (bit-shift-right e3 2))]
            (if (not= \= (nth enc 3))
              (let [d31 (bit-shift-left (bit-and 0x3 e3) 6)
                    d32 e4]
                (recur (nthrest enc 4)
                       (into decoded [(+ d11 d12) (+ d21 d22) (+ d31 d32)])))
              (into decoded [(+ d11 d12) (+ d21 d22)])))
          (conj decoded (+ d11 d12)))))))

; Binary encoding

(defn bincodes
  "Generate n binary codes."
  [n]
  (vec (for [i (range n)] (assoc (vec (repeat n 0)) i 1))))

(defn tobinary
  "Encode labels to a vector with 0 and 1. Also returns the vector of
  unique labels to decode them."
  [labels]
  (let [uniquelabels (unique labels)
        lbcodes      (bincodes (count uniquelabels))
        lb2code      (zipmap uniquelabels lbcodes)]
    [(vec (map lb2code labels)) uniquelabels]))

(defn frombinary
  "Decode a vector of 0 and 1 to the original label, based on a vector
  of unique labels."
  [uniquelabels encodedlabels]
  (let [lbcodes      (bincodes (count uniquelabels))
        code2lb      (zipmap lbcodes uniquelabels)]
    (vec (map code2lb encodedlabels))))

; Make clatrix matrices printable and readable in EDN format

(defmethod print-method
  clatrix.core.Matrix
  [^clatrix.core.Matrix mat ^java.io.Writer w]
  (.write w "#clatrix.core/Matrix ")
  (.write w (str (m/dense mat))))

(defn read-matrix
  "Reads a clatrix.core/Matrix in EDN format."
  [elems]
  (m/matrix elems))

; Save and load data in EDN format

(def datadir "data/")

(defn savedata
  "Save data in edn format to a given place with auto-generated name."
  [dataname data]
  (let [timestamp (System/currentTimeMillis)]
    (binding [*out* (FileWriter. (str datadir dataname "." timestamp) true)]
      (prn data))
    timestamp))

(defn loaddata
  "Load data previously saved with savedata."
  [dataname timestamp]
  (binding [*data-readers* (merge *data-readers* {'clatrix.core/Matrix read-matrix})]
    (read-string (slurp (str datadir dataname "." timestamp)))))

; Matrix utilities

(let [eps 1e-6]
  (defn quasi-equal?
    "True if 2 numbers are quasi equal (in the eps sense)"
    [n1 n2]
    (< (- eps) (- n1 n2) eps))
  (defn m-quasi-equal?
    "True if the dense data are quasi equal to the matrix content (in the eps sense)"
    [data mat]
    (every? true? (map quasi-equal? (apply concat data) (apply concat (m/dense mat))))))

(defn map2
  "Map a function to each pair of element of 2 matrices and returns the
  result as a third matrix of the same size (the 2 matrices must be the
  same size)."
  [f m1 m2]
  {:pre [(= (m/size m1) (m/size m2))]}
  (m/map-indexed
    (fn [i j a]
      (let [b (m/mget m2 i j)]
        (f a b))) m1))

(defn safe-div
  "Same as clatrix div, but avoid division by zero (returns m1 element)."
  [m1 m2]
  {:pre [(= (m/size m1) (m/size m2))]}
  (m/map-indexed
    (fn [i j a]
      (let [b (m/mget m2 i j)]
        (if (== b 0.0) a (/ a b)))) m1))

(defn with-bias
  "Add a column of ones at the left of the matrix."
  [mat]
  {:pre [(m/matrix? mat)]}
  (m/hstack (m/ones (first (m/size mat))) mat))

(defn without-bias
  "Take columns 2 to n of the matrix (i.e. drop the first column)."
  [mat]
  {:pre [(m/matrix? mat)]}
  (apply m/hstack (rest (m/cols mat))))

(defn read-samples-idx
  "Read samples (images) from a file encoded in IDX format."
  [fname]
  (let [f    (File. fname)
        dis  (DataInputStream. (FileInputStream. f))
        mn   (.readInt dis)]
    (assert (= mn 2051))
    (let [nsmp (.readInt dis)
          nrow (.readInt dis)
          ncol (.readInt dis)
          samples (vec (for [i (range nsmp)]
                         (vec (for [j (range (* nrow ncol))]
                                (int (bit-and 0xff (.readByte dis)))))))]
      (.close dis)
      samples)))

(defn read-labels-idx
  "Read labels from a file encoded in IDX format."
  [fname]
  (let [f    (File. fname)
        dis  (DataInputStream. (FileInputStream. f))
        mn   (.readInt dis)]
    (assert (= mn 2049))
    (let [nlb  (.readInt dis)
          labels (vec (for [i (range nlb)]
                        (int (bit-and 0xff (.readByte dis)))))]
      (.close dis)
      labels)))

;(def smp (read-samples-idx "data/t10k-images-idx3-ubyte"))
;(def lb  (read-labels-idx  "data/t10k-labels-idx1-ubyte"))
;(def trset (training-set smp lb {:rand false :batch 500 :nvalid 2000}))
;(savedata "trainingset" trset)

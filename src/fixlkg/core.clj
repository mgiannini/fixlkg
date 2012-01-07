(ns fixlkg.core
  (:import (java.net URL)
           (java.io BufferedInputStream  BufferedOutputStream
                    ByteArrayInputStream ByteArrayOutputStream
                    DataInputStream DataOutputStream)))

(def ^{:dynamic true} *mp3-in*)
(def ^{:dynamic true} *mp3-out*)

;; readers
(defn- dispatch [ftype]
  (cond
    (keyword? ftype) ftype
    (list? ftype) (first ftype)))

(defmulti read-a dispatch)

(defmethod read-a :byte [_]
  (.readByte *mp3-in*))

(defmethod read-a :char [_]
  (char (.readByte *mp3-in*)))

(defmethod read-a :int32 [_]
  (.readInt *mp3-in*))

(defmethod read-a :n-str [[_ length]]
  (let [bytes (byte-array length)]
    (.readFully *mp3-in* bytes)
    (String. bytes)))

(defmethod read-a :frame-size [_]
  (let [bytes (byte-array 3)]
    (.readFully *mp3-in* bytes)
    (+ (bit-shift-left (aget bytes 0) 16)
       (bit-shift-left (aget bytes 1) 8)
       (bit-shift-left (aget bytes 2) 0))))

(defmethod read-a :frame [_]
  (let [id (read-a '(:n-str 3))
        size (read-a :frame-size)
        bytes (byte-array size)]
    (.readFully *mp3-in* bytes)
    {:id id :size size :content bytes}))

(defmethod read-a :consume [[_ spec]]
  (loop [arr []]
    (let [s (try (read-a spec) (catch Exception e nil))]
      (if (nil? s)
        arr
        (recur (conj arr s))))))

;; spec
(defmacro defspec [spec-name & field-specs]
  `(def ~spec-name (make-spec ~(str spec-name) ~@field-specs)))

(defn make-spec [spec-name & field-specs]
  (loop [[spec & more-fields] field-specs
         fields []]
    (if spec
      (let [fname (first spec)
            ftype (second spec)]
        (recur more-fields (conj fields {:fname fname :ftype ftype})))
      {:name (str spec-name)
       :fields fields})))

(defn spec-read [spec]
  (loop [fields (:fields spec)
         data {}]
    (if fields
      (let [{:keys [fname ftype]} (first fields)
            fval (cond
                    ; basic type
                    (get-method read-a (dispatch ftype)) (read-a ftype)

                    ; sub-spec
                    (map? ftype) (spec-read ftype))]
        (recur (next fields) (assoc data fname fval)))
      data)))

;; mp3 spec

(def test-url (java.net.URL. "file:D:/temp/koine/test.mp3"))

(defspec v2-header
  [:id3 '(:n-str 3)]
  [:major :byte]
  [:minor :byte]
  [:flags :byte]
  [:tag-size :int32])

(defspec mp3-frame
  [:frame :frame]
(comment
  [:id '(:n-str 3)]
  [:size :frame-size]
  [:content :frame-content])
)

(defspec mp3
  [:header v2-header]
  [:frames '(:consume :frame)])

(defn mp3-decode
  ([] (mp3-decode test-url))
  ([url] (mp3-decode mp3 url))
  ([spec url]
  (with-open [ins (.openStream url)]
    (binding [*mp3-in* (-> ins (BufferedInputStream.) (DataInputStream.))]
      (spec-read spec)))))


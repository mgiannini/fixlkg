(ns fixlkg.fix
  (:gen-class)
  (:use [clojure.java.io :only [copy resource input-stream output-stream]]
        [clojure.tools.cli :only [cli]])
  (:import (java.io
              File
              ByteArrayOutputStream
              FileInputStream FileOutputStream
              BufferedInputStream  BufferedOutputStream
              DataInputStream DataOutputStream)))

(def ^{:dynamic true} *mp3-in*)
(def ^{:dynamic true} *mp3-out*)

;; readers
(defmulti read-a
  (fn [spec]
    (cond
      (keyword? spec) spec
      (map? spec) (:ftype spec))))

(defmethod read-a :byte [_]
  (.readByte *mp3-in*))

(defmethod read-a :n-str [spec]
  (let [bytes (byte-array (:len spec))]
    (.readFully *mp3-in* bytes)
    (String. bytes)))

(defmethod read-a :id3-size [_]
  (let [bytes (byte-array 4)]
    (.readFully *mp3-in* bytes)
    (+ (bit-shift-left (aget bytes 0) 23)
       (bit-shift-left (aget bytes 1) 15)
       (bit-shift-left (aget bytes 2) 7)
       (bit-shift-left (aget bytes 3) 0))))

(defmethod read-a :tag-size [_]
  (let [bytes (byte-array 3)]
    (.readFully *mp3-in* bytes)
    (+ (bit-shift-left (aget bytes 0) 16)
       (bit-shift-left (aget bytes 1) 8)
       (bit-shift-left (aget bytes 2) 0))))

(defmethod read-a :tags [spec]
  (loop [tags []
         bytes-left (:len spec)]
    (if (< bytes-left 0)
      tags
      (let [id (read-a {:ftype :n-str :len 3})
            tag-size (read-a :tag-size)
            bytes (byte-array tag-size)]
        (.readFully *mp3-in* bytes)
        (recur (conj tags {:id id :tag-size tag-size :content bytes})
               (- bytes-left (+ 6 tag-size)))))))

;; writers
(defmulti write-a
  (fn [spec val]
    (cond
      (keyword? spec) spec)))

(defmethod write-a :byte [_ val]
  (.writeByte *mp3-out* (int val)))

(defmethod write-a :int32 [_ val]
  (.writeInt *mp3-out* val))

(defmethod write-a :frame [_ val]
  (.write *mp3-out* (.getBytes (:id val)))
  (write-a :int32 (:size val))
  (.writeShort *mp3-out* (:flags val))
  (.write *mp3-out* (:content val)))

(defn make-text-frame [id content]
  {:id id
   :size (inc (count content))
   :flags 0
   :content (.getBytes (str "\0" content))})

(defn make-apic-frame [pic]
  (let [bout (ByteArrayOutputStream.)]
    (doto bout
      (.write 0)
      (.write (.getBytes "image/jpeg\0"))
      (.write 3)
      (.write 0))
    (with-open [in (if pic (input-stream pic) (input-stream (resource "rc/lkg.jpg")))]
      (copy in bout))
    {:id "APIC"
     :size (.size bout)
     :flags 0
     :content (.toByteArray bout)}))

(defn- calc-id3-size [frames]
  (let [size (reduce #(+ %1 (+ 10 (count (:content %2)))) 0 frames)]
    (+ (bit-and 0x7F size)
       (bit-and 0x7F00 (bit-shift-left size 1))
       (bit-and 0x7F0000 (bit-shift-left size 2))
       (bit-and 0x7F000000 (bit-shift-left size 3)))))

(defn id3-decode
  [file]
  (with-open [ins (-> file (FileInputStream.) (BufferedInputStream.) (DataInputStream.))]
    (binding [*mp3-in* ins]
      (let [id3 (read-a {:ftype :n-str :len 3})
            major (read-a :byte )
            minor (read-a :byte )
            flags (read-a :byte )
            id3-size (read-a :id3-size )]
        {:id3 id3
         :major major
         :minor minor
         :flags flags
         :size id3-size}))))

(defn id3-encode
  [file frames]
  (with-open [out (-> file (FileOutputStream.) (BufferedOutputStream.) (DataOutputStream.))]
    (binding [*mp3-out* out]
      (write-a :byte \I) (write-a :byte \D) (write-a :byte \3)
      (write-a :byte 3) (write-a :byte 0)
      (write-a :byte 0)
      (write-a :int32 (calc-id3-size frames))
      (doseq [frame frames] (write-a :frame frame)))))

(defn- parse-args [args]
  (let [[opts params usage] (cli args
          ["-a" "--album" "Album Title"]
          ["-p" "--pic" "Album cover picture (jpeg only)" :parse-fn #(File. %)]
          ["-o" "--out-dir" "Write converted mp3s to this directory"])
        usage (clojure.string/replace-first usage "Usage:"
                "\nUsage: fixlkg [options] <source mp3 directory>")
        out-dir (cond (:out-dir opts) (File. (:out-dir opts))
                      (:album opts) (File. (:album opts)))]
    (when (empty? params)
      (println "ERROR: source mp3 location not specified\n" usage)
      (System/exit 1))
    (when (nil? (:album opts))
      (println "ERROR: album option is required" usage)
      (System/exit 2))
    (when (and (not (.isDirectory out-dir)) (not (.mkdirs out-dir)))
      (println "ERROR: failed to create output directory:" out-dir usage)
      (System/exit 3))
    (when (and (:pic opts) (not (.isFile (:pic opts))))
      (println "ERROR: pic is not a file" (:pic opts))
      (System/exit 3))
    (let [file (File. (first params))]
      (when (not (.isDirectory file))
        (println "ERROR: mp3 location is not a directory" usage)
        (System/exit 4))
      (assoc opts :source file :dest out-dir))))

(defn- fix-mp3s [opts]
  (println "\nWriting converted mp3s to:" (.getAbsolutePath (:dest opts)))
  (println)
  (doseq [mp3 (filter #(re-matches #".*\.mp3$" (.getName %)) (.listFiles (:source opts)))]
    (let [orig-header (id3-decode mp3)
          mp3-name (->> mp3 (.getName) (drop-last 4) (apply str) (.trim))
          frames [(make-text-frame "TALB" (:album opts))
                  (make-text-frame "TIT2" mp3-name)
                  (make-text-frame "TPE1" "Randall Buth")
                  (make-text-frame "TCON" "Greek Language")
                  (make-apic-frame (:pic opts))]
          converted-mp3 (File. (:dest opts) (str mp3-name ".mp3"))]
      (id3-encode converted-mp3 frames)
      (with-open [ins (input-stream mp3)
                  out (output-stream converted-mp3 :append true)]
        (.skip ins (+ 10 (:size orig-header)))
        (copy ins out))
      (println (format "%-30s: Successfully converted!" (.getName mp3))))))

(defn -main [& args]
  (fix-mp3s (parse-args args)))


(ns cambada.cli
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.tools.cli :as cli]
            [clojure.tools.deps.alpha.reader :as deps.reader]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Console out and operating functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:dynamic *debug* (System/getenv "DEBUG"))

(defn debug
  "Print if *debug* (from DEBUG environment variable) is truthy."
  [& args]
  (when *debug* (apply println args)))

(def ^:dynamic *info* (not (System/getenv "LEIN_SILENT")))

(defn info
  "Print if *info* (from LEIN_SILENT environment variable) is truthy."
  [& args]
  (when *info* (apply println args)))

(defn warn
  "Print to stderr if *info* is truthy."
  [& args]
  (when *info*
    (binding [*out* *err*]
      (apply println args))))

(defn abort
  "Print msg to standard err and exit with a value of 1."
  [& msg]
  (binding [*out* *err*]
    (when (seq msg)
      (apply println msg))
    (System/exit 1)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helpers for cli use
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def base-cli-options
  [["-d" "--deps FILE_PATH" "Location of deps.edn file"
    :default "deps.edn"]

   ["-o" "--out PATH" "Output directory"
    :default "target"]

   ["-h" "--help" "Shows this help"]])

(defn ^:private args->parsed-opts
  [args cli-options]
  (cli/parse-opts args cli-options))

(defn ^:private parsed-opts->task
  [{{:keys [deps main aot] :as options} :options
    :keys [summary errors]}]
  (try
    (let [deps-map (deps.reader/slurp-deps (io/file deps))
          opts (cond-> options
                 ;; if main is not nil, it needs to be added to aot unless user chose all for aot
                 (and (not (nil? main))
                      (not= (first aot) 'all))
                 (assoc :aot (conj (or aot []) (symbol main))))]
      (-> {:parser {:summary summary
                    :errors errors}}
          (merge opts)
          (assoc :deps-map deps-map)))
    (catch Exception e
      (abort "Error reading your deps file. Make sure" deps "is existent and correct."))))

(defn args->task
  [args cli-options]
  (-> args
      (args->parsed-opts cli-options)
      parsed-opts->task))

(defn usage
  [main description task]
  (->>
   [description
    ""
    (str "Usage: clj -m " main " [options]")
    ""
    "Options:"
    (-> task :parser :summary)]
   (string/join \newline)
   info))

(defn runner
  [{:keys [help? task apply-fn entrypoint-main entrypoint-description]}]
  (if help?
    (usage entrypoint-main entrypoint-description task)
    (do (apply-fn task)
        (info "Done!"))))
(ns clara.examples.validation
  (:refer-clojure :exclude [==])
  (:require [clara.rules.accumulators :as acc]
            [clara.rules :as clara]
            [clj-time.core :as t]))

;;;; Facts used in the examples below.

(defrecord WorkOrder [clientid scale type requestdate duedate])

(defrecord ValidationError [reason description])

(defrecord ApprovalForm [formname])

(defrecord ClientTier [id tier])

(defn days-between
  "Returns the days between the start and end times."
  [start end]
  (t/in-days (t/interval start end)))

;;;; Some example rules. ;;;;

(clara/defrule large-job-delay
  "Large jobs must have at least a two week delay,
  unless it is a top-tier client"
  [WorkOrder (== ?clientid clientid)
             (= scale :big) 
             (< (days-between requestdate duedate) 14)]

  [:not [ClientTier
         (= ?clientid id) ; Join to the above client ID.
         (= tier :top)]]
  =>
  (insert! (->ValidationError
            :timeframe
            "Insufficient time prior to due date of the large order.")))

(clara/defrule hvac-approval
  "HVAC repairs need the appropriate paperwork."
  [WorkOrder (= type :hvac)]
  [:not [ApprovalForm (= formname "27B-6")]]
  =>
  (insert! (->ValidationError
            :approval
            "HVAC repairs must include a 27B-6 form.")))

(clara/defquery check-job
  "Checks the job for validation errors."
  []
  [?issue <- ValidationError])

;;;; Run the above example. ;;;;

(defn validate! [session]
  (doseq [result (clara/query session "clara.examples.validation/check-job")]
    (println "Validation issue: " (get-in result [:?issue :description]))))

(defn run-examples
  "Function to run the above example."
  []
  (println "Failed validation:")
  ;; Prints: "Validation error:  HVAC repairs must include a 27B-6 form."
  ;;         "Validation error:  Insufficient time prior to due date of the large order."
  (-> (clara/mk-session 'clara.examples.validation :cache false) ; Load the rules.
      (clara/insert (->WorkOrder 123
                                 :big
                                 :hvac
                                 (t/date-time 2013 8 2)
                                 (t/date-time 2013 8 5))) ; Insert some facts.
      (clara/fire-rules)
      (validate!))

  (println "Validation with appropriate client tier and paperwork.")
  (-> (clara/mk-session 'clara.examples.validation :cache false) ; Load the rules.
      (clara/insert (->WorkOrder 123
                                 :big
                                 :hvac
                                 (t/date-time 2013 8 2)
                                 (t/date-time 2013 8 5))
                    (->ClientTier 123 :top)
                    (->ApprovalForm "27B-6"))
      (clara/fire-rules)
      (validate!))

  nil)





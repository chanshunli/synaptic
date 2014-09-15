(ns synaptic.core-test
  (:require [clojure.test :refer :all]
            [synaptic.core :refer :all]
            [synaptic.util :refer :all]
            [clatrix.core :as m])
  (:import [synaptic.core Net DataSet TrainingSet Stats]))

(deftest test-activation-functions
  (testing "binary-threshold"
    (is (m-quasi-equal? [[1 0 1 0]]
                        (binary-threshold (m/matrix [[0 -0.001 0.001 -1000]])))))
  (testing "bipolar-threshold"
    (is (m-quasi-equal? [[1 -1 1 -1]]
                        (bipolar-threshold (m/matrix [[0 -0.001 0.001 -1000]])))))
  (testing "sigmoid"
    (is (m-quasi-equal? [[0.25 0.2]]
                        (sigmoid (m/matrix [[(Math/log 1/3) (Math/log 1/4)]])))))
  (testing "sample-with-p"
    (rand-set! 1)  ; 0.7308782 0.100473166 0.4100808 ...
    (is (m-quasi-equal? [[1 0 1]]
                        (sample-with-p (m/matrix [[0.74 0.09 0.58]])))))
  (testing "binary-stochastic"
    (rand-set! 1)  ; 0.7308782 0.100473166 0.4100808 ...
    (is (m-quasi-equal? [[0 1 0]]
                        (binary-stochastic (m/matrix [[0.5 -2.0 -0.5]])))))
  (testing "binary-prob"
    (is (m-quasi-equal? [[1 0 0]]
                        (binary-prob (m/matrix [[0.5 -2.0 -0.5]])))))
  (testing "softmax"
    (is (m-quasi-equal? [[0.0900306 0.2447285 0.6652410]
                         [0.6652410 0.2447285 0.0900306]]
                        (softmax (m/matrix [[1 2 3][2 1 0]]))))))

(defn mat-of-size? [mat m n]
  (and (m/matrix? mat)
       (= [m n] (m/size mat))))

(deftest test-weight-initialization
  (testing "init-all-weights"
    (let [w  (init-all-weights [5 3 2])]
      (is (vector? w))
      (is (= 2 (count w)))
      (let [w1 (first w)
            w2 (second w)]
        (is (mat-of-size? w1  3 6))
        (is (mat-of-size? w2 2 4))
        (let [elems1 (apply concat (m/dense w1))
              elems2 (apply concat (m/dense w2))
              min1 (apply min elems1)
              max1 (apply max elems1)
              min2 (apply min elems2)
              max2 (apply max elems2)]
          (is (< 0 max1 (* 100 init-eps)))
          (is (> 0 min1 (- (* 100 init-eps))))
          (is (< 0 max2 (* 100 init-eps)))
          (is (> 0 min2 (- (* 100 init-eps)))))))
    (is (thrown? java.lang.AssertionError (init-all-weights [])))
    (is (thrown? java.lang.AssertionError (init-all-weights [5])))))

(deftest test-neural-network
  (testing "neural-net"
    (let [nn @(neural-net [3 10 1] :sigmoid)]
      (is (= Net (type nn)))
      (let [w (:weights nn)
            a (-> nn :arch :act-fn)]
        (is (vector? w))
        (is (= 2 (count w)))
        (is (keyword? a))
        (is (= :sigmoid a))))
    (let [nn @(neural-net [6 4 4 2] :linear)]
      (is (= Net (type nn)))
      (let [w (:weights nn)
            a (-> nn :arch :act-fn)]
        (is (vector? w))
        (is (= 3 (count w)))
        (is (keyword? a))
        (is (= :linear a))))))

(deftest test-network-outputs
  (testing "valid-inputs?"
    (let [nn @(neural-net [3 4 2 1] :binary-prob)]
      (is (false? (valid-inputs? nn (m/matrix []))))
      (is (false? (valid-inputs? nn nil)))))
  (testing "activities"
    (let [w (m/matrix [[1 2 3][4 5 6]])
          x (m/matrix [[0 1][1 1][1 0][0 0]])]
      (is (= [[4.0 10.0] [6.0 15.0] [3.0 9.0] [1.0 4.0]]
             (m/dense (activities linear w x))))
      (is (= [[0.9820137900379085 0.9999546021312976]
              [0.9975273768433653 0.999999694097773]
              [0.9525741268224334 0.9998766054240137]
              [0.7310585786300049 0.9820137900379085]]
             (m/dense (activities sigmoid w x))))))
  (testing "net-activities"
    (let [nn  @(neural-net [3 2 1] :binary-threshold)
          x   (m/matrix [[0 1 0][1 0 1][1 1 0][1 0 0][1 1 1]])
          act (net-activities nn x)]
      (is (vector? act))
      (is (= 2 (count act)))
      (is (every? #(m/matrix? %) act))
      (is (every? #(= 5 (first (m/size %))) act))
      (is (= 2 (second (m/size (first act)))))
      (is (= 1 (second (m/size (second act))))))))

(deftest test-create-training-set
  (testing "shuffle-vecs"
    (let [v1 ["1" "2" "3" "4" "5" "6"]
          v2 [1 2 3 4 5 6]
          [shv1 shv2] (shuffle-vecs v1 v2)]
      (is (every? true? (map #(= (Integer/parseInt %1) %2) shv1 shv2)))))
  (testing "partition-vecs"
    (let [[batchsmp batchlb] (partition-vecs 3 [[0 1][1 1][1 0][0 0][1 1][0 1]
                                                [1 1][0 0][0 1][1 1][1 0]]
                                               [[0][1][1][0][1][0][0][1][1][0][1]])]
      (is (= 4 (count batchsmp) (count batchlb)))
      (is (= 3 (count (first batchsmp)) (count (first batchlb))))
      (is (= 2 (count (last batchsmp)) (count (last batchlb)))))
    (let [[batchsmp batchlb] (partition-vecs nil [[0 1][1 1][1 0][0 0][1 1][0 1]
                                                  [1 1][0 0][0 1][1 1][1 0]]
                                                 [[0][1][1][0][1][0][0][1][1][0][1]])]
      (is (= 1 (count batchsmp) (count batchlb)))
      (is (= 11 (count (first batchsmp)) (count (first batchlb))))))
  (testing "training-set"
    (let [ts (training-set [[1 0 1][0 1 1][0 0 1][1 1 0][0 0 0]
                            [0 1 1][1 1 0][1 0 1][0 1 0]]
                           ["b" "a" "a" "b" "a" "b" "b" "a" "b"] {:batch 2})]
      (is (= TrainingSet (type ts)))
      (let [bs   (:batches ts)
            vs   (:valid ts)
            ulbs (-> ts :header :labels)]
        (is (vector? bs))
        (is (= 5 (count bs)))
        (is (every? #(= DataSet (type %)) bs))
        (is (nil? vs))
        (is (= ["a" "b"] ulbs))
        (let [x (:x (first bs))
              y (:y (first bs))]
          (is (m/matrix? x))
          (is (m/matrix? y))
          (is (= [2 3] (m/size x)))
          (is (= [2 2] (m/size y))))))
    (let [ts (training-set [[1 0][0 0][0 1][1 0][0 1][1 1][1 0][0 1][1 1][0 0]]
                           ["2" "0" "1" "2" "1" "3" "2" "1" "3" "0"]
                           {:nvalid 3})]
      (is (= TrainingSet (type ts)))
      (let [bs   (:batches ts)
            vs   (:valid ts)
            ulbs (-> ts :header :labels)]
        (is (vector? bs))
        (is (= 1 (count bs)))
        (is (= DataSet (type (first bs))))
        (is (= DataSet (type vs)))
        (is (= ["0" "1" "2" "3"] ulbs))
        (let [x (:x (first bs))
              y (:y (first bs))]
          (is (m/matrix? x))
          (is (m/matrix? y))
          (is (= [7 2] (m/size x)))
          (is (= [7 4] (m/size y))))
        (let [x (:x vs)
              y (:y vs)]
          (is (m/matrix? x))
          (is (m/matrix? y))
          (is (= [3 2] (m/size x)))
          (is (= [3 4] (m/size y))))))
    (let [smp [[1 0 1 0][0 1 0 1][0 0 1 1][1 1 0 1][0 0 1 1]]
          ts  (training-set smp ["+" "-" "-" "+" "+"] {:online true :rand false})]
      (is (= TrainingSet (type ts)))
      (let [bs   (:batches ts)
            vs   (:valid ts)
            ulbs (-> ts :header :labels)]
        (is (vector? bs))
        (is (= 5 (count bs)))
        (is (= DataSet (type (first bs))))
        (is (nil? vs))
        (is (= ["+" "-"] ulbs))
        (is (every? true? (map #(= [(map double %1)]
                                   (m/dense (:x %2))) smp bs)))))))

(deftest test-perceptron-learning
  (testing "perceptron-error should return 1 if the output was 0 and target 1,
           -1 if the output was 1 and target 0, and 0 otherwise"
    (is (thrown? AssertionError
                 (perceptron-error [1 2 3] [4 5 6])))
    (is (thrown? AssertionError
                 (perceptron-error (m/matrix [[1 2 3][4 5 6]])
                                   (m/matrix [[1 2]  [3 4]  ]))))
    (is (= (m/matrix [[1][0][-1][0][-1]])
           (perceptron-error (m/matrix [[0][1][1][0][1]])
                             (m/matrix [[1][1][0][0][0]])))))
  (testing "deltaw :perceptron should compute the perceptron dw using
           perceptron-error multiplied by the inputs"
    (let [nn   (assoc @(neural-net [2 1] :binary-threshold (training :perceptron))
                      :weights [(m/matrix [[1 -1 2]])])
          ts   (dataset [[2 0][1 1][0 1][4 1][1 2]] [[1][1][0][0][0]])
          [nn2 [dw]] (deltaw nn ts)]
      (is (m/matrix? dw))
      (is (= (m/size dw) (m/size (first (:weights nn)))))
      ;(apply (partial map +) [[1 2 0][0 0 0][-1 0 -1][0 0 0][-1 -1 -2]])
      (is (= [[-1.0 1.0 -3.0]] (m/dense dw)))))
  (testing "update-weights should return a vector of updated weights, each
           one being the matrix sum of weights and deltaw"
    (let [updatedweights 
           (update-weights [(m/matrix [[1 -1 2]])]
                           [(m/matrix [[-1 1 -3]])])]
      (is (= 1 (count updatedweights)))
      (is (= [[0.0 0.0 -1.0]] (m/dense (first updatedweights))))))
  (testing "train-batch should perform one training batch, that is,
           update the weights based on one dataset"
    (let [nn   (assoc @(neural-net [2 1] :binary-threshold (training :perceptron))
                      :weights [(m/matrix [[1 -1 2]])])
          ds   (dataset [[2 0][1 1][0 1][4 1][1 2]] [[1][1][0][0][0]])
          nn2  (train-batch nn ds)
          w2   (:weights nn2)]
      (is (= 1 (count w2)))
      (is (= [[0.0 0.0 -1.0]] (m/dense (first w2)))))))

(deftest test-momentum
  (let [alpha   0.9
        prev-dw [(m/rand 5 11)]
        dw      [(m/rand 5 11)]
        nn      (assoc-in @(neural-net [10 5] :sigmoid (training :backprop))
                          [:training :state :deltaw] prev-dw)]
    (testing "momentum-factor should return nil if momentum is not defined"
      (let [[_ alpha-out] (momentum-factor nn)]
        (is (= nil alpha-out))))
    (testing "momentum-factor should return fixed alpha if alpha-start is not defined"
      (let [nn-mom (assoc-in nn [:training :params :momentum :alpha] alpha)
            [_ alpha-out] (momentum-factor nn-mom)]
        (is (= alpha alpha-out))))
    (testing "momentum-factor should return alpha-start if no previous alpha"
      (let [nn-mom (assoc-in nn [:training :params :momentum]
                             {:alpha alpha :alpha-start 0.5 :alpha-step 0.1})
            [_ alpha-out] (momentum-factor nn-mom)]
        (is (= 0.5 alpha-out))))
    (testing "momentum-factor should return previous alpha incremented by alpha-step"
      (let [nn-mom (assoc-in
                     (assoc-in nn [:training :params :momentum]
                               {:alpha alpha :alpha-start 0.5 :alpha-step 0.1})
                     [:training :state :alpha] 0.6)
            [_ alpha-out] (momentum-factor nn-mom)]
        (is (= 0.7 alpha-out))))
    (testing "momentum-factor should not increase alpha beyond max alpha"
      (let [nn-mom (assoc-in
                     (assoc-in nn [:training :params :momentum]
                               {:alpha 0.99 :alpha-start 0.5 :alpha-step 0.1})
                     [:training :state :alpha] 0.9)
            [_ alpha-out] (momentum-factor nn-mom)]
        (is (= 0.99 alpha-out))))
    (testing "apply-momentum should compute modified deltaw if momentum is specified"
      (let [nn-mom  (assoc-in nn [:training :params :momentum :alpha] alpha)
            [nn2 mom-dw] (apply-momentum nn-mom dw)]
        (is (= 1 (count mom-dw)))
        (is (= [5 11] (m/size (first mom-dw))))
        (for [i (range 5) j (range 11)]
          (is (quasi-equal? (m/mget (first mom-dw) i j)
                            (+ (* alpha (m/mget (first prev-dw) i j))
                               (m/mget (first dw) i j)))))))
    (testing "apply-momentum should return unmodified deltaw if no momentum specified"
      (let [[nn2 nomom-dw] (apply-momentum nn dw)]
        (is (= 1 (count nomom-dw)))
        (is (= [5 11] (m/size (first nomom-dw))))
        (for [i (range 5) j (range 11)]
          (is (quasi-equal? (m/mget (first nomom-dw) i j) (m/mget dw i j))))))
    (testing "momentum-deltaw should return unmodified deltaw if no previous deltaw"
      (let [nn-nodw (assoc-in (assoc-in nn [:training :params :momentum :alpha] alpha)
                              [:training :state] {})
            [nn2 nodw-dw] (apply-momentum nn-nodw dw)]
        (is (= 1 (count nodw-dw)))
        (is (= [5 11] (m/size (first nodw-dw))))
        (for [i (range 5) j (range 11)]
          (is (quasi-equal? (m/mget (first nodw-dw) i j) (m/mget dw i j))))))))

(deftest test-nesterov-momentum
  (let [nn (assoc @(neural-net [2 2] :sigmoid
                               (training :backprop {:momentum {:alpha 0.9}}))
                               :weights [(m/matrix [[1 -1 2][1 1 0]])])]
    (testing "apply-nesterov-momentum should initialize deltaw to 0 if no prev-dw"
      (let [[nn3 [dw]] (apply-nesterov-momentum nn)
            [w3] (:weights nn3)]
        (is (m-quasi-equal? [[0.0 0.0 0.0][0.0 0.0 0.0]] dw))
        (is (m-quasi-equal? [[1.0 -1.0 2.0][1.0 1.0 0.0]] w3))))
    (testing "apply-nesterov-momentum should update network weights with prev-deltaw"
      (let [pdw (m/matrix [[-0.5 -0.1 0.5][-0.1 0 0.5]])
            nn2 (assoc-in nn [:training :state :deltaw] [pdw])
            [nn3 [dw]] (apply-nesterov-momentum nn2)
            [w3] (:weights nn3)]
        (is (m-quasi-equal? [[-0.45 -0.09 0.45][-0.09 0.0 0.45]] dw))
        (is (m-quasi-equal? [[0.55 -1.09 2.45][0.91 1.0 0.45]] w3))))
    (testing "deltaw :nesterov should compute deltaw after updating network weights"
      (let [pdw (m/matrix [[-0.5 -0.1 0.5][-0.1 0 0.5]])
            nn2 (-> nn (assoc-in [:training :params :momentum :nesterov] true)
                       (assoc-in [:training :state :deltaw] [pdw])
                       (assoc-in [:training :params :learning-rate :epsilon] 1.0))
            dset {:x (m/matrix [[1 0][0 1][1 1][0 0]])
                  :y (m/matrix [[0 1][1 0][1 1][1 0]])}
            wt  [[0.55 -1.09 2.45][0.91 1.0 0.45]]
            nnb (assoc nn2 :weights [(m/matrix wt)])
            dw-exp (m/* -0.25 (first (error-derivatives nnb dset)))
            [nn3 [dw]] (deltaw nn2 dset)
            [w3] (:weights nn3)]
        (is (m-quasi-equal? (m/dense dw-exp) dw))
        
        (is (m-quasi-equal? (m/dense (m/+
                              (m/matrix [[-0.45 -0.09 0.45][-0.09 0.0 0.45]]) dw-exp))
                            (first (-> nn3 :training :state :deltaw))))
        (is (m-quasi-equal? wt w3))))))

(deftest test-training-error
  (testing "training-error should return the percentage of incorrect outputs
           for the specified dataset"
    (let [nn   (assoc @(neural-net [2 2] :binary-threshold (training :perceptron))
                      :weights [(m/matrix [[1 -1 -1][1 1 1]])])
          ts   (training-set [[2 0][0 1][2 1][4 1][1 2]] ["1" "1" "0" "0" "0"])
          err1 (training-error :misclassification nn ts)
          nn2  (train-batch nn (first (:batches ts)))
          err2 (training-error :misclassification nn2 ts)]
      (is (= [0.8] err1))
      (is (= [0.4] err2)))))

(deftest test-training-stats
  (testing "training-stats should create empty training statistics for the neural
           network"
    (let [nn    @(neural-net [2 1] :binary-threshold (training :perceptron))
          stats (training-stats nn)]
      (is (= Stats (type stats)))
      (is (= {:misclassification []} (:tr-err stats) (:val-err stats)))))
  (testing "update-stats should compute the training error (and optionally the
           validation error) for the neural network and update the stats"
    (let [nn     (assoc @(neural-net [2 2] :binary-threshold (training :perceptron))
                        :weights [(m/matrix [[1 -1 2][1 1 0]])])
          st     (training-stats nn)
          nn     (assoc-in nn [:training :stats] st)
          trset  (training-set [[2 0][1 1][0 1][4 1][1 2][1 0][1 2]]
                               ["b" "b" "a" "a" "a" "a" "b"]
                               {:online true :rand false :nvalid 2})
          nn1    (update-stats nn trset)
          st1    (-> nn1 :training :stats)
          nn2    (-> nn1 (train-batch (first (:batches trset)))
                         (update-stats trset))
          st2    (-> nn2 :training :stats)
          nn3    (-> nn2 (train-batch (second (:batches trset)))
                         (update-stats trset))
          st3    (-> nn3 :training :stats)]
      (is (= [0.8]         (-> st1 :tr-err :misclassification)))
      (is (= [1.0]         (-> st1 :val-err :misclassification)))
      (is (= [0.8 0.8]     (-> st2 :tr-err :misclassification)))
      (is (= [1.0 1.0]     (-> st2 :val-err :misclassification)))
      (is (= [0.8 0.8 0.6] (-> st3 :tr-err :misclassification)))
      (is (= [1.0 1.0 1.0] (-> st3 :val-err :misclassification)))))
  (testing "training-stats should initialize error vectors based on training params"
    (let [nn    @(neural-net [2 1] :sigmoid
                             (training :backprop
                                       {:stats {:errorkinds
                                                [:misclassification
                                                 :cross-entropy-binary]}}))
          stats (training-stats nn)]
      (is (= Stats (type stats)))
      (is (= {:misclassification [] :cross-entropy-binary []}
             (:tr-err stats) (:val-err stats))))))

(deftest test-train-network
  (let [net   (neural-net [3 2] :binary-threshold (training :perceptron))
        trset (training-set [[1 0 1][0 1 0][1 0 0][1 1 1]
                             [1 1 0][0 1 1][0 0 0][0 0 1]]
                            ["1" "0" "0" "1" "1" "1" "0" "0"]
                            {:online true :rand false :nvalid 2})]
    (testing "train should train a neural net and update statistics after each epoch"
      (let [net2 @(train net trset 1)
            st2 (-> @net :training :stats)
            net3 @(train net trset 2)
            st3 (-> @net :training :stats)
            net4 @(train net trset 10)
            st4 (-> @net :training :stats)]
        (is (= Stats (type st2)))
        (is (= 1 (count (-> st2 :tr-err :misclassification))
                 (count (-> st2 :val-err :misclassification))))
        (is (= Stats (type st3)))
        (is (= 3 (count (-> st3 :tr-err :misclassification))
                 (count (-> st3 :val-err :misclassification))))
        (is (= Stats (type st4)))
        (is (= 13 (count (-> st4 :tr-err :misclassification))
                  (count (-> st4 :val-err :misclassification))))))
    (testing "stop-training should stop the training before maxit is reached"
      (let [net2 (train net trset 1000)
            _    (Thread/sleep 20)
            nn3  (stop-training net)]
        (is (= :stopping (-> nn3 :training :state :state)))
        (is (> 1000 (-> @net2 deref :training :stats :epochs)))))))

(deftest test-backprop
  (testing "deltaw should propagate error derivatives to compute delta w"
    (let [nn    (assoc @(neural-net [3 2 2] :sigmoid
                         (training :backprop {:learning-rate {:epsilon 1.0}}))
                       :weights [(m/matrix [[ 0.1  0.11  0.09  0.095]
                                            [-0.1 -0.11 -0.09 -0.095]])
                                 (m/matrix [[ 1.05  0.098  1.03]
                                            [-1.05 -0.098 -1.03]])])
          trset (training-set [[1 0 1][0 1 1][0 0 0][1 0 1][0 1 0]]
                              ["1" "2" "2" "1" "2"]
                              :rand false)
          [nn2 dw] (deltaw nn (first (:batches trset)))]
      (is (= 2 (count dw)))
      (is (= [2 4] (m/size (nth dw 0))))
      (is (= [2 3] (m/size (nth dw 1))))
      (is (m-quasi-equal? [[-0.02070832 0.00337117 -0.01595879 -0.00454830]
                           [-0.21764871 0.03543173 -0.16773012 -0.04780355]]
                          (nth dw 0)))
      (is (m-quasi-equal? [[-0.42621045 -0.231439954 -0.19477050]
                           [ 0.42621045  0.231439954  0.19477050]]
                          (nth dw 1))))))

(deftest test-cost-functions
  (testing "misclassification should return 1 if the sample is misclassified"
    (is (= 1 (misclassification [1 0 1 0 1] [1 0 0 0 1])))
    (is (= 0 (misclassification [0 1 0 1 0] [0 1 0 1 0]))))
  (testing "sum-of-square should return the sum of square error of the sample"
    (is (quasi-equal? 1.0  (sum-of-squares [0.5 0.5 0.5 0.5] [1 0 1 0])))
    (is (quasi-equal? 0.04 (sum-of-squares [0.9 0.1 0.9 0.1] [1 0 1 0]))))
  (testing "cross-entropy should return the cross entropy of the sample"
    (is (quasi-equal? 1.38629436
                      (cross-entropy-multivariate [0.5 0.5 0.5 0.5] [1 0 1 0])))
    (is (quasi-equal? 0.21072103
                      (cross-entropy-multivariate [0.9 0.1 0.9 0.1] [1 0 1 0])))
    (is (quasi-equal? 0.10536052 (cross-entropy-binary [0.9] [1])))
    (is (quasi-equal? 2.30258509 (cross-entropy-binary [0.9] [0]))))
  (testing "cost-fn should return the cost function for a network or keyword"
    (let [nn1 @(neural-net [3 2] :binary-threshold (training :perceptron))
          nn2 @(neural-net [3 2] :sigmoid (training :backprop))
          nn3 @(neural-net [3 2] :softmax (training :backprop))
          nn4 @(neural-net [3 2] :sigmoid (training :backprop
                                                   {:cost-fn :sum-of-squares}))]
      (is (= #'misclassification (cost-fn nn1)))
      (is (= #'misclassification (cost-fn :misclassification)))
      (is (= #'cross-entropy-binary (cost-fn nn2)))
      (is (= #'cross-entropy-binary (cost-fn :cross-entropy-binary)))
      (is (= #'cross-entropy-multivariate (cost-fn nn3)))
      (is (= #'cross-entropy-multivariate (cost-fn :cross-entropy-multivariate)))
      (is (= #'sum-of-squares (cost-fn nn4)))
      (is (= #'sum-of-squares (cost-fn :sum-of-squares))))))

(deftest test-adaptive-learning-rate
  (let [dw  (m/matrix [[1 -1 1][-1 -1 1]])
        pdw (m/matrix [[1 1 -1][-1 -1 1]])
        gg  (m/matrix [[1 0.5 0.1][9.99 0.95 0.5]])]
    (testing "adapt-coef should increase the gain if dw and pdw are of same sign"
      (let [updfn (fn [d p g] (if (> (* d p) 0) (+ g 0.05) (* g 0.95)))
            gain (m/map-indexed (adapt-coef updfn 0.1 10.0 dw pdw) gg)]
        (is (m-quasi-equal? [[1.05 0.475 0.1][10.0 1.0 0.55]] gain))))
    (testing "adapt-learning-rate-gain should initialize the gain if uninitialized"
      (let [[gain] (adapt-learning-rate-gain 0.1 10.0 nil [dw] nil)]
        (is (= [[1.0 1.0 1.0][1.0 1.0 1.0]] (m/dense gain)))))
    (testing "adapt-learning-rate-gain should update the gain if initialized"
      (let [[gain] (adapt-learning-rate-gain 0.1 10.0 [gg] [dw] [pdw])]
        (is (m-quasi-equal? [[1.05 0.475 0.1][10.0 1.0 0.55]] gain))))
    (testing "apply-learning-rate should update the gain and compute deltaw"
      (let [nn (assoc-in @(neural-net [2 2] :sigmoid
                          (training :backprop
                                    {:learning-rate {:adaptive true :epsilon 0.01
                                                     :ming 0.1 :maxg 10.0}}))
                         [:training :state] {:deltaw [pdw] :lr-gain [gg]})
            [nn2 [dw2]] (apply-learning-rate nn [dw])]
        (is (m-quasi-equal? [[1.05 0.475 0.1][10.0 1.0 0.55]]
                            (-> nn2 :training :state :lr-gain first)))
        (is (m-quasi-equal? [[0.0105 -0.00475 0.001][-0.1 -0.01 0.0055]] dw2))))))

(deftest test-rprop
  (let [dw  (m/matrix [[1 -1 1][-1 -1 1]])
        pdw (m/matrix [[1 1 -1][-1 -1 1]])
        ss  (m/matrix [[1 0.5 0.1][9.99 0.95 0.5]])]
    (testing "adapt-rprop-step-size should initialize the step size if uninitialized"
      (let [[step] (adapt-rprop-step-size 0.1 10.0 nil [dw] nil)]
        (is (= [[1.0 1.0 1.0][1.0 1.0 1.0]] (m/dense step)))))
    (testing "adapt-rprop-step-size should update the step size if initialized"
      (let [[step] (adapt-rprop-step-size 0.1 10.0 [ss] [dw] [pdw])]
        (is (m-quasi-equal? [[1.2 0.25 0.1][10.0 1.14 0.6]] step))))
    (testing "adjust-step-size should update the step size and compute deltaw"
      (let [nn (assoc-in @(neural-net [2 2] :sigmoid
                          (training :rprop {:rprop {:mins 0.1 :maxs 10.0}}))
                         [:training :state] {:deltaw [pdw] :rp-step [ss]})
            [nn2 [dw2]] (adjust-step-size nn [dw])]
        (is (m-quasi-equal? [[1.2 0.25 0.1][10.0 1.14 0.6]]
                            (-> nn2 :training :state :rp-step first)))
        (is (m-quasi-equal? [[1.2 -0.25 0.1][-10.0 -1.14 0.6]] dw2))))
    (testing "deltaw should adjust step size and compute deltaw"
      (let [pdw (m/matrix [[1 1 -1][1 1 -1]])
            nn (assoc-in @(neural-net [2 2] :sigmoid
                          (training :rprop {:rprop {:mins 0.1 :maxs 10.0}}))
                         [:training :state] {:deltaw [pdw] :rp-step [ss]})
            ds (DataSet. (m/matrix [[1 0][0 0][0 1][1 1]])
                         (m/matrix [[0 1][1 1][1 0][0 0]]))
            ;dw sign will be [[1 -1 1][1 1 -1]]
            ;step update will be [[*1.2 *0.5 *0.5][*1.2 *1.2 *1.2]]
            [nn2 [dw2]] (deltaw nn ds)]
        (is (m-quasi-equal? [[1.2 0.25 0.1][10.0 1.14 0.6]]
                            (-> nn2 :training :state :rp-step first)))
        (is (= (-> nn2 :training :state :deltaw first) dw2))))))

(deftest test-rmsprop
  (let [pg [(m/matrix [[1.0 1.5 0.8][2.0 1.2 1.5]])
            (m/matrix [[1.2 0.8 0.5][2.1 0.5 1.6]])]
        g  [(m/matrix [[0.1 -0.2 0.5][-0.5 -0.1 0.4]])
            (m/matrix [[0.2 0.8 -0.2][-0.8 -0.2 0.5]])]
        alpha 0.9]
    (testing "update-ms-gradient should return square gradient if no prev-msg"
      (let [[ms1 ms2] (update-ms-gradient nil g alpha)]
        (is (m-quasi-equal? [[0.01 0.04 0.25][0.25 0.01 0.16]] ms1))
        (is (m-quasi-equal? [[0.04 0.64 0.04][0.64 0.04 0.25]] ms2))))
    (testing "update-ms-gradient should update mean sq grad with prev-msg and grad"
      (let [[ms1 ms2] (update-ms-gradient pg g alpha)]
        (is (m-quasi-equal? [[0.901 1.354 0.745][1.825 1.081 1.366]] ms1))
        (is (m-quasi-equal? [[1.084 0.784 0.454][1.954 0.454 1.465]] ms2))))
    (testing "apply-rms-gradient should update the mean sq grad and compute deltaw"
      (let [nn (assoc-in @(neural-net [2 2 2] :sigmoid (training :rmsprop))
                         [:training :state :ms-grad] pg)
            [nn2 dw2] (apply-rms-gradient nn g)
            [ms1 ms2] (-> nn2 :training :state :ms-grad)]
        (is (m-quasi-equal? [[0.901 1.354 0.745][1.825 1.081 1.366]] ms1))
        (is (m-quasi-equal? [[1.084 0.784 0.454][1.954 0.454 1.465]] ms2))))
    (testing "deltaw should update the mean sq grad and compute deltaw"
      (let [pdw (m/matrix [[1 1 -1][1 1 -1]])
            nn (assoc (assoc-in @(neural-net [2 2 2] :sigmoid (training :rmsprop))
                                [:training :state :ms-grad] pg)
                      :weights [(m/matrix [[0.1 0.4 0.2][0.4 0.5 0.2]])
                                (m/matrix [[0.05 0.1 0.08][0.06 0.05 0.12]])])
            ds (DataSet. (m/matrix [[1 0][0 0][0 1][1 1]])
                         (m/matrix [[0 1][1 1][1 0][0 0]]))
            ;dEdws will be [[[0.0035863 0.0254964 0.0134786]
            ;                [0.0023672 0.0180946 0.0268664]]
            ;               [[0.1634931 0.1938329 0.2195971]
            ;                [0.1706227 0.1500576 0.1591071]]]
            [nn2 dw2] (deltaw nn ds)
            [ms1 ms2] (-> nn2 :training :state :ms-grad)]
        ; mean sq grad = 0.9 * pg + 0.1 * dEdws^2
        (is (m-quasi-equal? [[0.9000013 1.3500650 0.7200182]
                             [1.8000006 1.0800327 1.3500722]] ms1))
        (is (m-quasi-equal? [[1.0826730 0.7237571 0.4548223]
                             [1.8929112 0.4522517 1.4425315]] ms2))
        (is (= (-> nn2 :training :state :deltaw) dw2))))))


(deftest test-weights-double-array
  (testing "it should be possible to convert weights to a double-array and vice versa"
    (let [nn @(neural-net [20 30 10] :sigmoid)
          ds (weights-to-double-array (:weights nn))]
      (is (= "class [D" (str (type ds))))
      (is (= (+ (* 30 21) (* 10 31)) (alength ^doubles ds)))
      (is (= (:weights nn) (double-array-to-weights ds (-> nn :arch :layers)))))))

(deftest test-lbfgs
  (let [lb ["0" "1" "2" "3" "4" "5" "6" "7" "8" "9"]
        k  (count lb)
        net (neural-net [20 30 k] [:sigmoid :softmax] (training :lbfgs))
        ts (training-set
             (vec (map vec (partition 20
               (for [i (range 500)] (Math/round ^double (rand))))))
             (vec (concat lb (map #(nth lb %) ; make sure all labels are included
               (for [i (range (- 25 k))] (Math/floor (* k ^double (rand))))))))]
    (testing "error+derivatives should produce a function to compute errors and deriv"
      (let [[fval gval] (error+derivatives @net (first (:batches ts)))]
        (is (= java.lang.Double (type fval)))
        (is (= "class [D" (str (type gval))))
        (is (= (+ (* 30 21) (* k 31)) (alength ^doubles gval)))))
    (testing "train :lbfgs should reduce the error"
      (let [[err1 _] (training-error :cross-entropy-multivariate @net ts)
            w1       (:weights @net)
            net2     @(train net ts 1)
            [err2 _] (training-error :cross-entropy-multivariate @net ts)
            w2       (:weights @net)]
        (is (not= w1 w2))
        (is (every? true? (map #(= (m/size %1) (m/size %2)) w1 w2)))
        (is (< err2 err1))))))


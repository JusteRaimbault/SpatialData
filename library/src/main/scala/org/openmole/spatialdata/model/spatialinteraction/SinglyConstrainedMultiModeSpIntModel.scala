package org.openmole.spatialdata.model.spatialinteraction

import org.openmole.spatialdata.model.spatialinteraction.SinglyConstrainedSpIntModel.averageTripLength
import org.openmole.spatialdata.utils
import org.openmole.spatialdata.utils.math.{EmptyMatrix, Matrix, SparseMatrix}
import org.openmole.spatialdata.vector.SpatialField

case class SinglyConstrainedMultiModeSpIntModel (
                                             modesObservedFlows: Array[Matrix],
                                             modesDistances: Array[Matrix],
                                             originValues: SpatialField[Double],
                                             destinationValues: SpatialField[Double],
                                             fittedParams: Array[Double],
                                             costFunction: (Double,Double)=> Double = {case (d,d0) => math.exp(-d / d0)},
                                             modesPredictedFlows: Array[Matrix] = Array.empty[Matrix]
                                           ) extends FittedSpIntModel {

  /**
    * Total observed flows are the sum of all modes
    * @return
    */
  override def observedFlows: Matrix = modesObservedFlows.reduce{case (phi1,phi2)=>phi1+phi2}

  /**
    * Several ways to compute an aggregate distance:
    *  - Average distance weighted by flows
    *  - raw average
    *  - min distance (deterministic mode choice)
    * @return
    */
  override def distances: Matrix = modesDistances.reduce{case (phi1,phi2)=>phi1+phi2}.map(_ / modesDistances.length)

  override def predictedFlows: Matrix = modesPredictedFlows.reduce{case (phi1,phi2)=>phi1+phi2}

  /**
    * rq: the generic function does not make sense as it fits itself in the end?
    * @return
    */
  override def fit: SpatialInteractionModel => SpatialInteractionModel = {
    s => s match {
      case m: SinglyConstrainedMultiModeSpIntModel =>
        SinglyConstrainedMultiModeSpIntModel.fitSinglyConstrainedMultiModeSpIntModel(m,
          m.fittedParams,
          SinglyConstrainedMultiModeSpIntModel.averageTripLength,
          true,
          0.01)
      case _ => throw new IllegalArgumentException("Can not fit other type of models")
    }
  }


}


object SinglyConstrainedMultiModeSpIntModel {


  /**
    * average trip length for each mode
    * @param model
    * @param phi
    * @return
    */
  def averageTripLength(model: SinglyConstrainedMultiModeSpIntModel, flowMatrices: Array[Matrix]): Array[Double] = {
    val phitots = flowMatrices.map(_.sum)
    val phinorm = flowMatrices.zip(phitots).map{case (phi,phitot) => phi.map(_ / phitot)}
    val wd = phinorm.zip(model.modesDistances).map{case (phi,d) => phi*d}
    wd.map(_.sum)
  }

  /**
    * TODO run with sparse matrices for perf (after having benchmarked sparse mat impls)
    * @param model Model to fir
    * @param objectiveFunction statistic compared between the two models
    * @param originConstraint constraints at the origin?
    * @param convergenceThreshold
    * @return
    */
  def fitSinglyConstrainedMultiModeSpIntModel(model: SinglyConstrainedMultiModeSpIntModel,
                                              initialValues: Array[Double],
                                              objectiveFunction: (SinglyConstrainedMultiModeSpIntModel,Array[Matrix]) => Array[Double] = averageTripLength,
                                              originConstraint: Boolean = true,
                                              convergenceThreshold: Double = 0.01
                                    ): SinglyConstrainedMultiModeSpIntModel = {

    // ! force a SparseMatrix here
    val origin = utils.timerLog[Unit,Matrix](_ => SparseMatrix(model.originValues.values.flatten.toArray,false),(),"origin column matrix")
    val destination = utils.timerLog[Unit,Matrix](_ => SparseMatrix(model.destinationValues.values.flatten.toArray,false),(),"destination column matrix")
    println(s"origin column mat = ${origin}")

    val obsObjective = utils.timerLog[Unit,Array[Double]](_ => objectiveFunction(model,model.modesObservedFlows),(),"objective cost function")
    utils.log(s"observed stat = $obsObjective")

    val initialFlows = singlyConstrainedMultiModeFlows(
        origin,
        destination,
        model.modesDistances.zip(initialValues).map{case (dmat,d0) => dmat.map(model.costFunction(_,d0))},
        originConstraint
    )

    val initialModel = model.copy(modesPredictedFlows=initialFlows, fittedParams = initialValues)

    /**
      * State is (model including cost function, current parameter value, epsilon)
      *  epsilon is max of epsilon for each mode
      * @param state
      * @return
      */
    def iterateCostParam(state: (SinglyConstrainedMultiModeSpIntModel,Double)):  (SinglyConstrainedMultiModeSpIntModel,Double) = {
      val t = System.currentTimeMillis()
      val model = state._1
      val fitparameters = model.fittedParams
      utils.log(s"parameter = ${fitparameters.toSeq}")
      val currentCostMatrices = utils.timerLog[Unit,Array[Matrix]](_ => model.modesDistances.zip(fitparameters).map{case (dmat,d0) => dmat.map(model.costFunction(_,d0))},(),"current cost matrix")
      val predictedFlows = utils.timerLog[Unit,Array[Matrix]](_ => singlyConstrainedMultiModeFlows(origin,destination,currentCostMatrices,originConstraint),(),"singly constrained flows")

      val predObjectives = utils.timerLog[Unit,Array[Double]](_ => objectiveFunction(model,predictedFlows),(),"predicted cost function")
      utils.log(s"predicted stat = ${predObjectives.toSeq}")

      // ! with the form exp(-d/d0), inverse than exp(-beta d)
      val newfitparameters = fitparameters.zip(predObjectives).zip(obsObjective).map{case ((d0,cbar),c) => d0*c/cbar}
      val newmodel = model.copy(modesPredictedFlows = predictedFlows, fittedParams = newfitparameters)
      val errors = predObjectives.zip(obsObjective).map{case (cbar,c) => math.abs(c - cbar)/c}
      utils.log(s"fit singly constr multi mode: errors = ${errors.toSeq} ; iteration in ${System.currentTimeMillis()-t}")
      (newmodel,errors.max)
    }

    val res = Iterator.iterate((initialModel,Double.MaxValue.toDouble))(iterateCostParam).takeWhile(_._2>convergenceThreshold).toSeq.last
    res._1
  }



  /**
    * Multi mode flows (normalization is done on sum of all modes)
    *
    * Do a separate function than the single mode for readibility of usage in single mode
    *
    * @param originMasses
    * @param destinationMasses
    * @param costMatrix
    * @param originConstraint
    * @return
    */
  def singlyConstrainedMultiModeFlows(originMasses: Matrix,
                                      destinationMasses: Matrix,
                                      costMatrices: Array[Matrix],
                                      originConstraint: Boolean
                                     ): Array[Matrix] = {
    val totalCostMatrix = costMatrices.reduce { case (c1, c2) => c1 + c2 } // cost Theta(c * NModes)
    val normalization = (if (originConstraint) SparseMatrix.diagonal((totalCostMatrix %*% destinationMasses).values.flatten) else SparseMatrix.diagonal((totalCostMatrix %*% originMasses).values.flatten)).map(1 / _)

    val origin = SparseMatrix.diagonal(originMasses.flatValues)
    val destination = SparseMatrix.diagonal(destinationMasses.flatValues)
    val omat = if (originConstraint) origin %*% normalization else origin
    val dmat = if (originConstraint) destination else destination %*% normalization
    costMatrices.map(omat %*% _ %*% dmat) // cost Theta(c * NModes)
  }

}




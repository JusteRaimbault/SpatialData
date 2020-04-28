package org.openmole.spatialdata.utils.math

import scala.util.Random

object Stochastic {


  sealed trait Distribution {
    def rng: Random
    def draw: Double
  }

  case class NormalDistribution(mu: Double, sigma: Double)(implicit localRng: Random) extends Distribution {
    override def rng: Random = localRng
    override def draw: Double = mu + sigma*rng.nextGaussian()
  }

  case class LogNormalDistribution(mu: Double, sigma: Double)(implicit localRng: Random) extends Distribution {
    override def rng: Random = localRng
    override def draw: Double = math.exp(mu + sigma*rng.nextGaussian())
  }
  object LogNormalDistribution {
    def fromMoments(average: Double, std: Double)(implicit localRng: Random): LogNormalDistribution = {
      assert(average>0,"Average of a log-normal should be strictly positive")
      assert(std>0,"Std of a log-normal should be strictly positive")
      assert(math.log(std)<math.log(average)-0.5,"Std of log normal too low regarding average")
      LogNormalDistribution(
        mu = 2 * math.log(average) - math.log(std) - 0.5,
        sigma = math.sqrt(1 - 2*(math.log(average) - math.log(std)))
      )
    }
  }


  /**
    * The probability function is assumed to effectively define a probability measure on elements of sampled
    * @param sampled
    * @param probability
    * @param rng
    * @tparam T
    * @return
    */
  def sampleOneBy[T](sampled: Iterable[T], probability: T => Double)(implicit rng: Random): T = {
    def f(s: (Iterable[T],T,Double,Double)): (Iterable[T], T, Double, Double) = {//s._1.size match{
    //case 0 => (s._1,)
    // case _ =>
     // println(s._3 + probability (s._1.head));println(s._3+" ; "+s._4)
        (s._1.tail, s._1.head, s._3 + probability (s._1.head), s._4)
    }
    f(Iterator.iterate((sampled,sampled.head,0.0,rng.nextDouble()))(f).takeWhile(s => s._3 < s._4&&s._1.nonEmpty).toSeq.last)._2
  }

  def sampleWithReplacementBy[T](sampled: Iterable[T], probability: T => Double, samples: Int)(implicit rng: Random): Vector[T] =
    (0 until samples).map(_ => sampleOneBy(sampled, probability)).toVector

  // FIXME fails on a Set -> be less general than Traversable ?
  def sampleWithReplacement[T](sampled: Iterable[T], samples: Int)(implicit rng: Random): Vector[T] =
    sampleWithReplacementBy[T](sampled,_ => 1.0 / sampled.size.toDouble, samples)

  def sampleWithoutReplacementBy[T](sampled: Iterable[T], probability: T => Double, samples: Int)(implicit rng: Random): Vector[T] = {
    assert(samples <= sampled.size,"Can not sample more than vector size : "+samples+" / "+sampled.size)
    Iterator.iterate((sampled, Vector.empty[T])) { case (rest, res) => {
      val totproba = rest.map(probability(_)).sum // FIXME not efficient, could be computed from the previously sampled proba
      val normalizedProba = rest.toSeq.map(probability(_) / totproba)
      val sample = sampleOneBy[((T, Int), Double)](rest.toSeq.zipWithIndex.zip(normalizedProba), _._2)
      (rest.toSeq.zipWithIndex.filter(_._2 != sample._1._2).map(_._1), Vector(sample._1._1) ++ res)
    }
    }.take(samples).toSeq.last._2
  }

  def sampleWithoutReplacement[T](sampled: Iterable[T], samples: Int)(implicit rng: Random): Vector[T] =
    sampleWithoutReplacementBy[T](sampled,_ => 1.0 / sampled.size.toDouble, samples)

}

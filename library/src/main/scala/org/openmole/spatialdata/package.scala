package org.openmole

package object spatialdata {

  val DEBUG = true


  /**
    * RasterLayerData are two dimensional arrays of Numeric values
    * TODO keep the name RasterLayer for a wrapper with more properties
    * FIXME mutable ! -> switch to Vector[Vector]
    */
  type RasterLayerData[N] = Array[Array[N]]

  /*
  implicit class RasterLayerDataDecorator[T,U](d: RasterLayerData[T]) {
    def mapElementWise(f: T => U): RasterLayerData[U] = d.map{row: Array[T] => row.map(f).toArray[U]}
  }*/

  // this breaks everything
  //implicit def rasterLayerDataIsVector[N](r: RasterLayerData[N]): Vector[Vector[N]] = r.map{_.toVector}.toVector


  /**
    * RasterData sequence of layer data
    */
  type RasterData[N] = Seq[RasterLayerData[N]]

  type RasterDim = Either[Int,(Int,Int)]


  /**
    * Point in 2D
    */
  type Point2D = (Double,Double)

  /**
    * Geographical coordinate (lon,lat)
    */
  type Coordinate = (Double,Double)


  /**
    * Spatial field
    */
  type SpatialField = Map[Point2D,Array[Double]]


  object Implicits {

    implicit def rasterDimConversion(i:Int): RasterDim = Left(i)
    implicit def rasterDimConversion(c:(Int,Int)): RasterDim = Right(c)

  }



}

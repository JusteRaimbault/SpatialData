package org.openmole.spatialdata.test

import java.io.File
import org.openmole.spatialdata.application.urbanmorphology.GridGeneratorLauncher
import org.openmole.spatialdata.grid.real.OSMGridSampling
import org.openmole.spatialdata.utils.io.PNG

import scala.util.Random

object TestPNG {

  implicit val doubleOrdering: Ordering[Double] = Ordering.Double.TotalOrdering

  def testPNG(): Unit = {

    implicit val rng: Random = new Random(42L)
    val launchers = Seq("random","expMixture","blocks","percolation").map{
      GridGeneratorLauncher(_,200,
        0.5,
        200,10.0,0.5,
        80,10,30,
        0.2,20,4.0)
    }

    val directory = new File("data/test")
    directory.mkdirs()
    launchers.foreach{
       g =>
        val grid = g.getGrid
        PNG.write(grid, new File(s"data/test/${g.generatorType}.png"))
    }
  }
  def testOSMGridSampling(): Unit = {
    implicit val rng: Random = new Random

    val grids = OSMGridSampling.sampleGridsInLayer("data/cities_europe.shp",100,500,50)

    val directory = new File("data/test")
    directory.mkdirs()

    for (agrid <- grids) {
      if (agrid._2.map(_.max).max > 0) {
//        println(grid.gridToString(agrid._2))
        PNG.write(agrid._2, new File(s"data/test/osm_${agrid._1}.png"))
      }
    }
  }
}

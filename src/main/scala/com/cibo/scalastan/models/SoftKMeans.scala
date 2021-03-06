/*
 * Copyright (c) 2017 - 2018 CiBO Technologies - All Rights Reserved
 * You may use, distribute, and modify this code under the
 * terms of the BSD 3-Clause license.
 *
 * A copy of the license can be found on the root of this repository,
 * at https://github.com/cibotech/ScalaStan/blob/master/LICENSE,
 * or at https://opensource.org/licenses/BSD-3-Clause
 */

package com.cibo.scalastan.models

import com.cibo.scalastan._

case class SoftKMeans(
  clusterCount: Int,              // Number of clusters
  observations: Seq[Seq[Double]]  // Observations
) extends ScalaStan {

  // Soft K-Means
  // from "Stan Modeling Language: User's Guide and Reference Manual" version 2.16.0.

  private val n = data(int(lower = 0))    // Number of data points
  private val d = data(int(lower = 1))    // Number of dimensions
  private val k = data(int(lower = 1))    // Number of clusters
  private val y = data(vector(d)(n))

  val mu: ParameterDeclaration[StanArray[StanVector]] = parameter(vector(d)(k))  // Cluster means

  private val negLogK = new TransformedData(real(upper = 0)) {
    result := -stan.log(k)
  }

  val softZ = new TransformedParameter(real(upper = 0)(n, k)) {
    for (i <- range(1, n)) {
      for (j <- range(1, k)) {
        result(i, j) := negLogK - 0.5 * stan.dot_self(mu(j) - y(i))
      }
    }
  }

  private val model = new Model {
    // Prior
    for (i <- range(1, k)) {
      mu(i) ~ stan.normal(0, 1)
    }

    // Likelihood
    for(i <- range(1, n)) {
      target += stan.log_sum_exp(softZ(i))
    }
  }

  def compile[M <: CompiledModel](implicit runner: StanRunner[M]): CompiledModel = model.compile
    .withData(k, clusterCount)
    .withData(y, observations)

  def clusterAssignments(results: StanResults): Seq[Int] = {
    results.best(softZ.result).map(_.zipWithIndex.maxBy(_._1)._2)
  }
}

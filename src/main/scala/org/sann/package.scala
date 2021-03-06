/**
 * Copyright (c) 2014 Liang Kun. All Rights Reserved.
 * Authors: Liang Kun <liangkun@data-intelli.com>
 */

package org

import scala.language.implicitConversions

import breeze.linalg._
import breeze.stats.distributions.Rand

package object sann {
  /** Type of an activating function (vectorized). */
  case class Activator(evaluate: (DenseVector[Double] => DenseVector[Double]),
                       derivate: (DenseVector[Double] => DenseVector[Double]))

  /**
   * A group of isomorphism neurons.
   *
   * @param cardinality number of neurons in this group.
   * @param activator activating function of the neurons.
   * @param inputs synapses that provide inputs to each of the neuron in this group, order is important.
   */
  case class Neurons(cardinality: Int, activator: Activator, inputs: List[Synapses] = List()) {
    def ->(neurons: Neurons) = neurons.copy(inputs = Synapses(this) :: neurons.inputs)
  }

  /**
   * A group of synapses that have the input neurons connected as input.
   *
   * @param input input neurons.
   */
  case class Synapses(input: Neurons)

  /**
   * Input (from external) neurons of the network.
   *
   * @param cardinality of the input neurons.
   */
  def inputs(cardinality: Int) = Neurons(cardinality, linearActivator, List())

  /** Linear(identity) Activator */
  val linearActivator = Activator(
    evaluate = x => x,
    derivate = x => DenseVector.ones(x.length)
  )

  /**
   * Compiled Sann with compact internal representation, ready for training and working.
   *
   * Currently only support linear layers.
   */
  class CompiledSann(
    val neuronses: Array[Neurons],
    val weights: Array[DenseMatrix[Double]],
    val impulses: Array[DenseVector[Double]],
    val errors: Array[DenseVector[Double]]
  ) {

  }

  def compile(output: Neurons): CompiledSann = {
    val neuronses = topologySort(output).toArray
    val numNeurons = neuronses.size
    assert(neuronses(numNeurons - 1) == output, "Topology sort error ?")

    val weights = Array.tabulate(numNeurons) { idx =>
      val neurons = neuronses(idx)
      if (neurons.inputs.isEmpty) {
        null  // networks input weights should never be used
      } else {
        val inputSize = neurons.inputs.foldLeft(0)((size, input) => size + input.input.cardinality)
        val layerSize = neurons.cardinality
        val weight = DenseMatrix.rand[Double](layerSize, inputSize, Rand.uniform)
        weight :*= 2.0
        weight :+= -1.0
        weight
      }
    }

    val impulses = Array.tabulate(numNeurons) { idx =>
      val size = neuronses(idx).cardinality
      DenseVector.zeros[Double](size)
    }

    val errors = Array.tabulate(numNeurons) { idx =>
      val size = neuronses(idx).cardinality
      DenseVector.zeros[Double](size)
    }

    new CompiledSann(neuronses, weights, impulses, errors)
  }

  def topologySort(neurons: Neurons, seen: Set[Neurons] = Set()): List[Neurons] = {
    reverseTopologySort(neurons, seen).reverse
  }

  def reverseTopologySort(neurons: Neurons, seen: Set[Neurons] = Set()): List[Neurons] = {
    if (seen.contains(neurons)) {
      List()
    } else {
      val updatedSeen = seen + neurons
      var result = List(neurons)
      for (input <- neurons.inputs) {
        result ++= reverseTopologySort(input.input, updatedSeen)
      }
      result
    }
  }

  // Number of all the neurons that can reach the specified neurons(including).
  def reachingNeuronsNum(neurons: Neurons, seen: Set[Neurons] = Set()): Int = {
    if (seen.contains(neurons)) {
      0
    } else {
      var count = 1
      val updatedSeen = seen + neurons
      for (input <- neurons.inputs) {
        count += reachingNeuronsNum(input.input, updatedSeen)
      }
      count
    }
  }

  // =============================================================================================
  // Internal
  // =============================================================================================

  def perceptronLearner(
    weights: DenseMatrix[Double],
    input: DenseVector[Double],
    error: DenseVector[Double],
    info: Option[Any]): Option[Any] = {

    weights(::, *) += input * error(0)

    None
  }
}

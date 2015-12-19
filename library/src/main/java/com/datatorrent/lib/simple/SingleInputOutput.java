package com.datatorrent.lib.simple;

import com.datatorrent.api.DefaultInputPort;
import com.datatorrent.api.DefaultOutputPort;
import com.datatorrent.api.annotation.Name;
import com.datatorrent.api.annotation.Stateless;
import com.datatorrent.common.util.BaseOperator;

/**
 * A base implementation of an operator that abstracts away the input and output port.
 *
 * Subclasses should provide the implementation to process a tuple of type I and return a tuple of
 * type O.
 *
 * <p>
 * <b>Input Port :</b><br>
 * <b> input :</b> default input port. <br>
 * <br>
 * <b>Output Port :</b><br>
 * <b> output :</b> default output port. <br>
 * <br>
 * <b>Stateful : No</b>, all state is handled through the implementing class. <br>
 * <b>Partitions : Yes</b>, no dependency among input tuples. <br>
 * <br>
 * @displayName Single Input & Output
 * @category Simple Operators
 * @tags simple, single input, single output
 * @param <I> type being received from the input port
 * @param <O> type being sent from the output port
 * @since 3.3.0
 */
@Stateless
@Name("single-input-output")
public abstract class SingleInputOutput<I, O> extends BaseOperator
{
  protected final transient DefaultOutputPort<O> output = new DefaultOutputPort<O>();
  protected final transient DefaultInputPort<I> input = new DefaultInputPort<I>() {
    @Override
    public void process(I inputTuple)
    {
      output.emit(SingleInputOutput.this.process(inputTuple));
    }
  };

  abstract O process(I inputTuple);
}

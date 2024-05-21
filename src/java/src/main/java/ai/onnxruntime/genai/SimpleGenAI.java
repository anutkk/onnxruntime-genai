package ai.onnxruntime.genai;

import java.util.function.Consumer;

/**
 * The `SimpleGenAI` class provides a simple usage example of the GenAI API. It works with a model
 * that generates text based on a prompt, processing a single prompt at a time.
 *
 * <p>Usage:
 *
 * <ul>
 *   <li>Create an instance of the class with the path to the model. The path should also contain
 *       the GenAI configuration files.
 *   <li>Call createGeneratorParams with the prompt text.
 *   <li>Set any other search options via the GeneratorParams object as needed using
 *       `setSearchOption`.
 *   <li>Call generate with the GeneratorParams object and an optional listener.
 * </ul>
 *
 * <p>The listener is used as a callback mechanism so that tokens can be used as they are generated.
 * Create a class that implements the TokenUpdateListener interface and provide an instance of that
 * class as the `listener` argument.
 */
public class SimpleGenAI {
  private Model model;
  private Tokenizer tokenizer;

  public SimpleGenAI(String modelPath) throws GenAIException {
    model = new Model(modelPath);
    tokenizer = model.createTokenizer();
  }

  /**
   * Create the generator parameters and add the prompt text. The user can set other search options
   * via the GeneratorParams object prior to running `generate`.
   *
   * @param prompt The prompt text to encode.
   * @return The generator parameters.
   * @throws GenAIException on failure
   */
  public GeneratorParams createGeneratorParams(String prompt) throws GenAIException {
    GeneratorParams generatorParams = model.createGeneratorParams();

    try (Sequences encodedPrompt = tokenizer.encode(prompt)) {
      generatorParams.setInput(encodedPrompt);
    } catch (GenAIException e) {
      generatorParams.close();
      throw e;
    }

    return generatorParams;
  }

  /**
   * Create the generator parameters and add the prompt text. The user can set other search options
   * via the GeneratorParams object prior to running `generate`.
   *
   * @return The generator parameters.
   * @throws GenAIException on failure
   */
  public GeneratorParams createGeneratorParams(int[] tokenIds, int sequenceLength, int batchSize)
      throws GenAIException {
    GeneratorParams generatorParams = model.createGeneratorParams();
    try {
      generatorParams.setInput(tokenIds, sequenceLength, batchSize);
    } catch (GenAIException e) {
      generatorParams.close();
      throw e;
    }

    return generatorParams;
  }

  /**
   * Generate text based on the prompt and settings in GeneratorParams.
   *
   * <p>NOTE: This only handles a single sequence of input (i.e. a single prompt which equates to
   * batch size of 1)
   *
   * @param generatorParams The prompt and settings to run the model with.
   * @param listener Optional callback for tokens to be provided as they are generated. NOTE: Token
   *     generation will be blocked until the listener's `accept` method returns.
   * @return The generated text.
   * @throws GenAIException on failure
   */
  public String generate(GeneratorParams generatorParams, Consumer<String> listener)
      throws GenAIException {
    String result;
    try {
      int[] output_ids;

      if (listener != null) {
        try (TokenizerStream stream = tokenizer.createStream();
            Generator generator = new Generator(model, generatorParams)) {
          while (!generator.isDone()) {
            // generate next token
            generator.computeLogits();
            generator.generateNextToken();

            // decode and call listener
            int token_id = generator.getLastTokenInSequence(0);
            String token = stream.decode(token_id);
            listener.accept(token);
            // listener.onTokenGenerate(token);
          }

          output_ids = generator.getSequence(0);
        } catch (GenAIException e) {
          throw new GenAIException("Token generation loop failed.", e);
        }
      } else {
        Sequences output_sequences = model.generate(generatorParams);
        output_ids = output_sequences.getSequence(0);
      }

      result = tokenizer.decode(output_ids);
    } catch (GenAIException e) {
      throw new GenAIException("Failed to create Tokenizer.", e);
    }

    return result;
  }
}

/*
 *  Copyright 2016 Rodrigo Agerri

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package eus.ixa.ixa.pipe.ml;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;

import eus.ixa.ixa.pipe.ml.features.XMLFeatureDescriptor;
import eus.ixa.ixa.pipe.ml.formats.CoNLL02Format;
import eus.ixa.ixa.pipe.ml.formats.CoNLL03Format;
import eus.ixa.ixa.pipe.ml.formats.LemmatizerFormat;
import eus.ixa.ixa.pipe.ml.formats.TabulatedFormat;
import eus.ixa.ixa.pipe.ml.resources.LoadModelResources;
import eus.ixa.ixa.pipe.ml.sequence.BilouCodec;
import eus.ixa.ixa.pipe.ml.sequence.BioCodec;
import eus.ixa.ixa.pipe.ml.sequence.SequenceLabelSample;
import eus.ixa.ixa.pipe.ml.sequence.SequenceLabelSampleTypeFilter;
import eus.ixa.ixa.pipe.ml.sequence.SequenceLabelerCodec;
import eus.ixa.ixa.pipe.ml.sequence.SequenceLabelerEvaluator;
import eus.ixa.ixa.pipe.ml.sequence.SequenceLabelerFactory;
import eus.ixa.ixa.pipe.ml.sequence.SequenceLabelerME;
import eus.ixa.ixa.pipe.ml.sequence.SequenceLabelerModel;
import eus.ixa.ixa.pipe.ml.utils.Flags;
import eus.ixa.ixa.pipe.ml.utils.IOUtils;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.TrainingParameters;

/**
 * Trainer based on Apache OpenNLP Machine Learning API. This class creates a
 * feature set based on the features activated in the trainParams.properties
 * file:
 * <ol>
 * <li>Window: specify left and right window lengths.
 * <li>TokenFeatures: tokens as features in a window length.
 * <li>TokenClassFeatures: token shape features in a window length.
 * <li>WordShapeSuperSenseFeatures: token shape features from Ciaramita and
 * Altun (2006).
 * <li>OutcomePriorFeatures: take into account previous outcomes.
 * <li>PreviousMapFeatures: add features based on tokens and previous decisions.
 * <li>SentenceFeatures: add beginning and end of sentence words.
 * <li>PrefixFeatures: first 4 characters in current token.
 * <li>SuffixFeatures: last 4 characters in current token.
 * <li>BigramClassFeatures: bigrams of tokens and token class.
 * <li>TrigramClassFeatures: trigrams of token and token class.
 * <li>FourgramClassFeatures: fourgrams of token and token class.
 * <li>FivegramClassFeatures: fivegrams of token and token class.
 * <li>CharNgramFeatures: character ngram features of current token.
 * <li>DictionaryFeatures: check if current token appears in some gazetteer.
 * <li>ClarkClusterFeatures: use the clustering class of a token as a feature.
 * <li>BrownClusterFeatures: use brown clusters as features for each feature
 * containing a token.
 * <li>Word2VecClusterFeatures: use the word2vec clustering class of a token as
 * a feature.
 * <li>POSTagModelFeatures: use pos tags, pos tag class as features.
 * <li>LemmaModelFeatures: use lemma as features.
 * <li>LemmaDictionaryFeatures: use lemma from a dictionary as features.
 * <li>MFSFeatures: Most Frequent sense feature.
 * <li>SuperSenseFeatures: Ciaramita and Altun (2006) features for super sense
 * tagging.
 * <li>POSBaselineFeatures: train a baseline POS tagger.
 * <li>LemmaBaselineFeatures: train a baseline Lemmatizer.
 * <li>ChunkBaselineFeatures: train a baseline chunker.
 * </ol>
 * 
 * @author ragerri
 * @version 2016-05-06
 */
public class SequenceLabelerTrainer {

  /**
   * The language.
   */
  private final String lang;
  /**
   * String holding the training data.
   */
  private final String trainData;
  /**
   * String holding the testData.
   */
  private final String testData;
  /**
   * ObjectStream of the training data.
   */
  private ObjectStream<SequenceLabelSample> trainSamples;
  /**
   * ObjectStream of the test data.
   */
  private ObjectStream<SequenceLabelSample> testSamples;
  /**
   * The corpus format: conll02, conll03, lemmatizer, tabulated.
   */
  private final String corpusFormat;
  /**
   * The sequence encoding of the named entity spans, e.g., BIO or BILOU.
   */
  private String sequenceCodec;
  /**
   * Reset the adaptive features every newline in the training data.
   */
  private final String clearTrainingFeatures;
  /**
   * Reset the adaptive features every newline in the testing data.
   */
  private final String clearEvaluationFeatures;
  /**
   * features needs to be implemented by any class extending this one.
   */
  private SequenceLabelerFactory nameClassifierFactory;

  /**
   * Construct a trainer with training and test data, and with options for
   * language, beamsize for decoding, sequence codec and corpus format (conll or
   * opennlp).
   * 
   * @param params
   *          the training parameters
   * @throws IOException
   *           io exception
   */
  public SequenceLabelerTrainer(final TrainingParameters params)
      throws IOException {

    this.lang = Flags.getLanguage(params);
    this.clearTrainingFeatures = Flags.getClearTrainingFeatures(params);
    this.clearEvaluationFeatures = Flags.getClearEvaluationFeatures(params);
    this.corpusFormat = Flags.getCorpusFormat(params);
    this.trainData = params.getSettings().get("TrainSet");
    this.testData = params.getSettings().get("TestSet");
    this.trainSamples = getSequenceStream(this.trainData,
        this.clearTrainingFeatures, this.corpusFormat);
    this.testSamples = getSequenceStream(this.testData,
        this.clearEvaluationFeatures, this.corpusFormat);
    this.sequenceCodec = Flags.getSequenceCodec(params);
    if (params.getSettings().get("Types") != null) {
      final String netypes = params.getSettings().get("Types");
      final String[] neTypes = netypes.split(",");
      this.trainSamples = new SequenceLabelSampleTypeFilter(neTypes,
          this.trainSamples);
      this.testSamples = new SequenceLabelSampleTypeFilter(neTypes,
          this.testSamples);
    }
    createSequenceLabelerFactory(params);
  }

  /**
   * Create {@code SequenceLabelerFactory} with custom features.
   *
   * @param params
   *          the parameter training file
   * @throws IOException
   *           if io error
   */
  public void createSequenceLabelerFactory(final TrainingParameters params)
      throws IOException {
    final String seqCodec = getSequenceCodec();
    final SequenceLabelerCodec<String> sequenceCodec = SequenceLabelerFactory
        .instantiateSequenceCodec(seqCodec);
    final String featureDescription = XMLFeatureDescriptor
        .createXMLFeatureDescriptor(params);
    System.err.println(featureDescription);
    final byte[] featureGeneratorBytes = featureDescription
        .getBytes(Charset.forName("UTF-8"));
    final Map<String, Object> resources = LoadModelResources
        .loadSequenceResources(params);
    setSequenceLabelerFactory(
        SequenceLabelerFactory.create(SequenceLabelerFactory.class.getName(),
            featureGeneratorBytes, resources, sequenceCodec));
  }

  public final SequenceLabelerModel train(final TrainingParameters params) {
    if (getSequenceLabelerFactory() == null) {
      throw new IllegalStateException(
          "The SequenceLabelerFactory must be instantiated!!");
    }
    SequenceLabelerModel trainedModel = null;
    try {
      trainedModel = SequenceLabelerME.train(this.lang, this.trainSamples,
          params, this.nameClassifierFactory);
      final SequenceLabelerME seqLabeler = new SequenceLabelerME(trainedModel);
      trainingEvaluate(seqLabeler);
    } catch (final IOException e) {
      System.err.println("IO error while loading traing and test sets!");
      e.printStackTrace();
      System.exit(1);
    }
    return trainedModel;
  }

  private void trainingEvaluate(final SequenceLabelerME sequenceLabeler) {
    if (this.corpusFormat.equalsIgnoreCase("lemmatizer")
        || this.corpusFormat.equalsIgnoreCase("tabulated")) {
      final SequenceLabelerEvaluator evaluator = new SequenceLabelerEvaluator(
          this.trainSamples, this.corpusFormat, sequenceLabeler);
      try {
        evaluator.evaluate(this.testSamples);
      } catch (final IOException e) {
        e.printStackTrace();
      }
      System.out.println();
      System.out.println("Word Accuracy: " + evaluator.getWordAccuracy());
      System.out
          .println("Sentence Accuracy: " + evaluator.getSentenceAccuracy());
    } else {
      final SequenceLabelerEvaluator evaluator = new SequenceLabelerEvaluator(this.corpusFormat,
          sequenceLabeler);
      try {
        evaluator.evaluate(this.testSamples);
      } catch (final IOException e) {
        e.printStackTrace();
      }
      System.out.println("Final Result: \n" + evaluator.getFMeasure());
    }
  }

  /**
   * Getting the stream with the right corpus format.
   * 
   * @param inputData
   *          the input data
   * @param clearFeatures
   *          clear the features
   * @param aCorpusFormat
   *          the corpus format
   * @return the stream from the several corpus formats
   * @throws IOException
   *           the io exception
   */
  public static ObjectStream<SequenceLabelSample> getSequenceStream(
      final String inputData, final String clearFeatures,
      final String aCorpusFormat) throws IOException {
    ObjectStream<SequenceLabelSample> samples = null;
    if (aCorpusFormat.equalsIgnoreCase("conll03")) {
      final ObjectStream<String> nameStream = IOUtils
          .readFileIntoMarkableStreamFactory(inputData);
      samples = new CoNLL03Format(clearFeatures, nameStream);
    } else if (aCorpusFormat.equalsIgnoreCase("conll02")) {
      final ObjectStream<String> nameStream = IOUtils
          .readFileIntoMarkableStreamFactory(inputData);
      samples = new CoNLL02Format(clearFeatures, nameStream);
    } else if (aCorpusFormat.equalsIgnoreCase("tabulated")) {
      final ObjectStream<String> nameStream = IOUtils
          .readFileIntoMarkableStreamFactory(inputData);
      samples = new TabulatedFormat(clearFeatures, nameStream);
    } else if (aCorpusFormat.equalsIgnoreCase("lemmatizer")) {
      final ObjectStream<String> seqStream = IOUtils
          .readFileIntoMarkableStreamFactory(inputData);
      samples = new LemmatizerFormat(clearFeatures, seqStream);
    } else {
      System.err.println("Test set corpus format not valid!!");
      System.exit(1);
    }
    return samples;
  }

  /**
   * Get the features which are implemented in each of the trainers extending
   * this class.
   * 
   * @return the features
   */
  public final SequenceLabelerFactory getSequenceLabelerFactory() {
    return this.nameClassifierFactory;
  }

  public final SequenceLabelerFactory setSequenceLabelerFactory(
      final SequenceLabelerFactory tokenNameFinderFactory) {
    this.nameClassifierFactory = tokenNameFinderFactory;
    return this.nameClassifierFactory;
  }

  /**
   * Get the Sequence codec.
   * 
   * @return the sequence codec
   */
  public final String getSequenceCodec() {
    String seqCodec = null;
    if ("BIO".equals(this.sequenceCodec)) {
      seqCodec = BioCodec.class.getName();
    } else if ("BILOU".equals(this.sequenceCodec)) {
      seqCodec = BilouCodec.class.getName();
    }
    return seqCodec;
  }

  /**
   * Set the sequence codec.
   * 
   * @param aSeqCodec
   *          the sequence codec to be set
   */
  public final void setSequenceCodec(final String aSeqCodec) {
    this.sequenceCodec = aSeqCodec;
  }
}

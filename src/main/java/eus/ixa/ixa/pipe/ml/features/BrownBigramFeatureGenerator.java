/*
 * Copyright 2014 Rodrigo Agerri

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
package eus.ixa.ixa.pipe.ml.features;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import eus.ixa.ixa.pipe.ml.resources.WordCluster;
import opennlp.tools.util.InvalidFormatException;
import opennlp.tools.util.featuregen.ArtifactToSerializerMapper;
import opennlp.tools.util.featuregen.CustomFeatureGenerator;
import opennlp.tools.util.featuregen.FeatureGeneratorResourceProvider;
import opennlp.tools.util.model.ArtifactSerializer;

public class BrownBigramFeatureGenerator extends CustomFeatureGenerator
    implements ArtifactToSerializerMapper {

  private WordCluster brownLexicon;
  private Map<String, String> attributes;

  public BrownBigramFeatureGenerator() {
  }

  @Override
  public void createFeatures(final List<String> features, final String[] tokens,
      final int index, final String[] previousOutcomes) {

    final List<String> wordClasses = BrownTokenClasses
        .getWordClasses(tokens[index], this.brownLexicon);
    if (index > 0) {
      final List<String> prevWordClasses = BrownTokenClasses
          .getWordClasses(tokens[index - 1], this.brownLexicon);
      for (int i = 0; i < wordClasses.size()
          && i < prevWordClasses.size(); i++) {
        features.add("p" + this.attributes.get("dict") + ","
            + this.attributes.get("dict") + "=" + prevWordClasses.get(i) + ","
            + wordClasses.get(i));
      }
    }
    // this is a bug, but it seems to work better
    if (index + 1 > tokens.length) {
      final List<String> nextWordClasses = BrownTokenClasses
          .getWordClasses(tokens[index + 1], this.brownLexicon);
      for (int i = 0; i < wordClasses.size()
          && i < nextWordClasses.size(); i++) {
        features.add(this.attributes.get("dict") + "," + "n"
            + this.attributes.get("dict") + "=" + wordClasses.get(i) + ","
            + nextWordClasses.get(i));
      }
    }
  }

  @Override
  public void updateAdaptiveData(final String[] tokens,
      final String[] outcomes) {

  }

  @Override
  public void clearAdaptiveData() {

  }

  @Override
  public void init(final Map<String, String> properties,
      final FeatureGeneratorResourceProvider resourceProvider)
      throws InvalidFormatException {
    final Object dictResource = resourceProvider
        .getResource(properties.get("dict"));
    if (!(dictResource instanceof WordCluster)) {
      throw new InvalidFormatException(
          "Not a ClusterLexicon resource for key: " + properties.get("dict"));
    }
    this.brownLexicon = (WordCluster) dictResource;
    this.attributes = properties;

  }

  @Override
  public Map<String, ArtifactSerializer<?>> getArtifactSerializerMapping() {
    final Map<String, ArtifactSerializer<?>> mapping = new HashMap<>();
    mapping.put("brownserializer", new WordCluster.WordClusterSerializer());
    return Collections.unmodifiableMap(mapping);
  }

}

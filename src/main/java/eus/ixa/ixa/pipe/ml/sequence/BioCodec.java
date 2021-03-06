/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eus.ixa.ixa.pipe.ml.sequence;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eus.ixa.ixa.pipe.ml.utils.Span;

public class BioCodec implements SequenceLabelerCodec<String> {

  public static final String START = "start";
  public static final String CONTINUE = "cont";
  public static final String OTHER = "other";

  private static final Pattern typedOutcomePattern = Pattern
      .compile("(.+)-\\S+");

  public static final String extractSequenceType(final String outcome) {
    final Matcher matcher = typedOutcomePattern.matcher(outcome);
    if (matcher.matches()) {
      final String seqType = matcher.group(1);
      return seqType;
    }

    return null;
  }

  @Override
  public Span[] decode(final List<String> c) {
    int start = -1;
    int end = -1;
    final List<Span> spans = new ArrayList<Span>(c.size());
    for (int li = 0; li < c.size(); li++) {
      final String chunkTag = c.get(li);
      if (chunkTag.endsWith(BioCodec.START)) {
        if (start != -1) {
          spans.add(new Span(start, end, extractSequenceType(c.get(li - 1))));
        }

        start = li;
        end = li + 1;

      } else if (chunkTag.endsWith(BioCodec.CONTINUE)) {
        end = li + 1;
      } else if (chunkTag.endsWith(BioCodec.OTHER)) {
        if (start != -1) {
          spans.add(new Span(start, end, extractSequenceType(c.get(li - 1))));
          start = -1;
          end = -1;
        }
      }
    }

    if (start != -1) {
      spans.add(new Span(start, end, extractSequenceType(c.get(c.size() - 1))));
    }

    return spans.toArray(new Span[spans.size()]);
  }

  @Override
  public String[] encode(final Span[] sequences, final int length) {
    final String[] outcomes = new String[length];
    for (int i = 0; i < outcomes.length; i++) {
      outcomes[i] = BioCodec.OTHER;
    }
    for (final Span sequence : sequences) {
      if (sequence.getType() == null) {
        outcomes[sequence.getStart()] = "default" + "-" + BioCodec.START;
      } else {
        outcomes[sequence.getStart()] = sequence.getType() + "-"
            + BioCodec.START;
      }
      // now iterate from begin + 1 till end
      for (int i = sequence.getStart() + 1; i < sequence.getEnd(); i++) {
        if (sequence.getType() == null) {
          outcomes[i] = "default" + "-" + BioCodec.CONTINUE;
        } else {
          outcomes[i] = sequence.getType() + "-" + BioCodec.CONTINUE;
        }
      }
    }

    return outcomes;
  }

  @Override
  public SequenceLabelerSequenceValidator createSequenceValidator() {
    return new SequenceLabelerSequenceValidator();
  }

  @Override
  public boolean areOutcomesCompatible(final String[] outcomes) {
    // We should have *optionally* one outcome named "other", some named
    // xyz-start and sometimes
    // they have a pair xyz-cont. We should not have any other outcome
    // To validate the model we check if we have one outcome named "other", at
    // least
    // one outcome with suffix start. After that we check if all outcomes that
    // ends with
    // "cont" have a pair that ends with "start".
    final List<String> start = new ArrayList<String>();
    final List<String> cont = new ArrayList<String>();

    for (final String outcome : outcomes) {
      if (outcome.endsWith(START)) {
        start.add(outcome.substring(0, outcome.length() - START.length()));
      } else if (outcome.endsWith(CONTINUE)) {
        cont.add(outcome.substring(0, outcome.length() - CONTINUE.length()));
      } else if (outcome.equals(OTHER)) {
        // don't fail anymore if couldn't find outcome named OTHER
      } else {
        // got unexpected outcome
        return false;
      }
    }

    if (start.size() == 0) {
      return false;
    } else {
      for (final String contPreffix : cont) {
        if (!start.contains(contPreffix)) {
          return false;
        }
      }
    }

    return true;
  }
}

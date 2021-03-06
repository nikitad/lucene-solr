package org.apache.solr.spelling;

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.util.Comparator;

import org.apache.lucene.analysis.Token;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.spell.DirectSpellChecker;
import org.apache.lucene.search.spell.StringDistance;
import org.apache.lucene.search.spell.SuggestWord;
import org.apache.lucene.search.spell.SuggestWordFrequencyComparator;
import org.apache.lucene.search.spell.SuggestWordQueue;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrCore;
import org.apache.solr.search.SolrIndexSearcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spellchecker implementation that uses {@link DirectSpellChecker}
 * <p>
 * Requires no auxiliary index or data structure.
 * <p>
 * Supported options:
 * <ul>
 *   <li>field: Used as the source of terms.
 *   <li>distanceMeasure: Sets {@link DirectSpellChecker#setDistance(StringDistance)}. 
 *       Note: to set the default {@link DirectSpellChecker#INTERNAL_LEVENSHTEIN}, use "internal".
 *   <li>accuracy: Sets {@link DirectSpellChecker#setAccuracy(float)}.
 *   <li>maxEdits: Sets {@link DirectSpellChecker#setMaxEdits(int)}.
 *   <li>minPrefix: Sets {@link DirectSpellChecker#setMinPrefix(int)}.
 *   <li>maxInspections: Sets {@link DirectSpellChecker#setMaxInspections(int)}.
 *   <li>comparatorClass: Sets {@link DirectSpellChecker#setComparator(Comparator)}.
 *       Note: score-then-frequency can be specified as "score" and frequency-then-score
 *       can be specified as "freq".
 *   <li>thresholdTokenFrequency: sets {@link DirectSpellChecker#setThresholdFrequency(float)}.
 *   <li>minQueryLength: sets {@link DirectSpellChecker#setMinQueryLength(int)}.
 *   <li>maxQueryFrequency: sets {@link DirectSpellChecker#setMaxQueryFrequency(float)}.
 * </ul>
 * @see DirectSpellChecker
 */
public class DirectSolrSpellChecker extends SolrSpellChecker {
  private static final Logger LOG = LoggerFactory.getLogger(DirectSolrSpellChecker.class);
  
  /** Field to use as the source of terms */
  public static final String FIELD = "field";
  
  public static final String STRING_DISTANCE = "distanceMeasure";
  public static final String INTERNAL_DISTANCE = "internal";
  
  public static final String ACCURACY = "accuracy";
  public static final float DEFAULT_ACCURACY = 0.5f;
  
  public static final String MAXEDITS = "maxEdits";
  public static final int DEFAULT_MAXEDITS = 2;
  
  public static final String MINPREFIX = "minPrefix";
  public static final int DEFAULT_MINPREFIX = 1;
  
  public static final String MAXINSPECTIONS = "maxInspections";
  public static final int DEFAULT_MAXINSPECTIONS = 5;

  public static final String COMPARATOR_CLASS = "comparatorClass";
  public static final String SCORE_COMP = "score";
  public static final String FREQ_COMP = "freq";

  public static final String THRESHOLD = "thresholdTokenFrequency";
  public static final float DEFAULT_THRESHOLD = 0.0f;
  
  public static final String MINQUERYLENGTH = "minQueryLength";
  public static final int DEFAULT_MINQUERYLENGTH = 4;
  
  public static final String MAXQUERYFREQUENCY = "maxQueryFrequency";
  public static final float DEFAULT_MAXQUERYFREQUENCY = 0.01f;
  
  private DirectSpellChecker checker = new DirectSpellChecker();
  private String field;
  
  @Override
  public String init(NamedList config, SolrCore core) {
    LOG.info("init: " + config);
    String name = super.init(config, core);
    
    Comparator<SuggestWord> comp = SuggestWordQueue.DEFAULT_COMPARATOR;
    String compClass = (String) config.get(COMPARATOR_CLASS);
    if (compClass != null) {
      if (compClass.equalsIgnoreCase(SCORE_COMP))
        comp = SuggestWordQueue.DEFAULT_COMPARATOR;
      else if (compClass.equalsIgnoreCase(FREQ_COMP))
        comp = new SuggestWordFrequencyComparator();
      else //must be a FQCN
        comp = (Comparator<SuggestWord>) core.getResourceLoader().newInstance(compClass);
    }
    
    StringDistance sd = DirectSpellChecker.INTERNAL_LEVENSHTEIN;
    String distClass = (String) config.get(STRING_DISTANCE);
    if (distClass != null && !distClass.equalsIgnoreCase(INTERNAL_DISTANCE))
      sd = (StringDistance) core.getResourceLoader().newInstance(distClass);

    field = (String) config.get(FIELD);
    
    float minAccuracy = DEFAULT_ACCURACY;
    String accuracy = (String) config.get(ACCURACY);
    if (accuracy != null)
      minAccuracy = Float.parseFloat(accuracy);
    
    int maxEdits = DEFAULT_MAXEDITS;
    String edits = (String) config.get(MAXEDITS);
    if (edits != null)
      maxEdits = Integer.parseInt(edits);
    
    int minPrefix = DEFAULT_MINPREFIX;
    String prefix = (String) config.get(MINPREFIX);
    if (prefix != null)
      minPrefix = Integer.parseInt(prefix);
    
    int maxInspections = DEFAULT_MAXINSPECTIONS;
    String inspections = (String) config.get(MAXINSPECTIONS);
    if (inspections != null)
      maxInspections = Integer.parseInt(inspections);
    
    float minThreshold = DEFAULT_THRESHOLD;
    String threshold = (String) config.get(THRESHOLD);
    if (threshold != null)
      minThreshold = Float.parseFloat(threshold);
    
    int minQueryLength = DEFAULT_MINQUERYLENGTH;
    String queryLength = (String) config.get(MINQUERYLENGTH);
    if (queryLength != null)
      minQueryLength = Integer.parseInt(queryLength);
    
    float maxQueryFrequency = DEFAULT_MAXQUERYFREQUENCY;
    String queryFreq = (String) config.get(MAXQUERYFREQUENCY);
    if (queryFreq != null)
      maxQueryFrequency = Float.parseFloat(queryFreq);
    
    checker.setComparator(comp);
    checker.setDistance(sd);
    checker.setMaxEdits(maxEdits);
    checker.setMinPrefix(minPrefix);
    checker.setAccuracy(minAccuracy);
    checker.setThresholdFrequency(minThreshold);
    checker.setMaxInspections(maxInspections);
    checker.setMinQueryLength(minQueryLength);
    checker.setMaxQueryFrequency(maxQueryFrequency);
    checker.setLowerCaseTerms(false);
    
    return name;
  }
  
  @Override
  public void reload(SolrCore core, SolrIndexSearcher searcher)
      throws IOException {}

  @Override
  public void build(SolrCore core, SolrIndexSearcher searcher) {}

  @Override
  public SpellingResult getSuggestions(SpellingOptions options)
      throws IOException {
    LOG.debug("getSuggestions: " + options.tokens);
    
    SpellingResult result = new SpellingResult();
    float accuracy = (options.accuracy == Float.MIN_VALUE) ? checker.getAccuracy() : options.accuracy;
    
    for (Token token : options.tokens) {
      SuggestWord[] suggestions = checker.suggestSimilar(new Term(field, token.toString()), 
          options.count, options.reader, options.onlyMorePopular, accuracy);
      for (SuggestWord suggestion : suggestions)
        result.add(token, suggestion.string, suggestion.freq);
    }
    return result;
  }
}

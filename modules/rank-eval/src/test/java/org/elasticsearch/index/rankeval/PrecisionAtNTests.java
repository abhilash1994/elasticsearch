/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.index.rankeval;

import org.elasticsearch.common.ParseFieldMatcher;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.rankeval.PrecisionAtN.Rating;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchShardTarget;
import org.elasticsearch.search.internal.InternalSearchHit;
import org.elasticsearch.test.ESTestCase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

public class PrecisionAtNTests extends ESTestCase {

    public void testPrecisionAtFiveCalculation() {
        List<RatedDocument> rated = new ArrayList<>();
        rated.add(new RatedDocument("test", "testtype", "0", Rating.RELEVANT.ordinal()));
        EvalQueryQuality evaluated = (new PrecisionAtN(5)).evaluate("id", toSearchHits(rated, "test", "testtype"), rated);
        assertEquals(1, evaluated.getQualityLevel(), 0.00001);
        assertEquals(1, ((PrecisionAtN.Breakdown) evaluated.getMetricDetails()).getRelevantRetrieved());
        assertEquals(1, ((PrecisionAtN.Breakdown) evaluated.getMetricDetails()).getRetrieved());
    }

    public void testPrecisionAtFiveIgnoreOneResult() {
        List<RatedDocument> rated = new ArrayList<>();
        rated.add(new RatedDocument("test", "testtype", "0", Rating.RELEVANT.ordinal()));
        rated.add(new RatedDocument("test", "testtype", "1", Rating.RELEVANT.ordinal()));
        rated.add(new RatedDocument("test", "testtype", "2", Rating.RELEVANT.ordinal()));
        rated.add(new RatedDocument("test", "testtype", "3", Rating.RELEVANT.ordinal()));
        rated.add(new RatedDocument("test", "testtype", "4", Rating.IRRELEVANT.ordinal()));
        EvalQueryQuality evaluated = (new PrecisionAtN(5)).evaluate("id", toSearchHits(rated, "test", "testtype"), rated);
        assertEquals((double) 4 / 5, evaluated.getQualityLevel(), 0.00001);
        assertEquals(4, ((PrecisionAtN.Breakdown) evaluated.getMetricDetails()).getRelevantRetrieved());
        assertEquals(5, ((PrecisionAtN.Breakdown) evaluated.getMetricDetails()).getRetrieved());
    }

    /**
     * test that the relevant rating threshold can be set to something larger than 1.
     * e.g. we set it to 2 here and expect dics 0-2 to be not relevant, doc 3 and 4 to be relevant
     */
    public void testPrecisionAtFiveRelevanceThreshold() {
        List<RatedDocument> rated = new ArrayList<>();
        rated.add(new RatedDocument("test", "testtype", "0", 0));
        rated.add(new RatedDocument("test", "testtype", "1", 1));
        rated.add(new RatedDocument("test", "testtype", "2", 2));
        rated.add(new RatedDocument("test", "testtype", "3", 3));
        rated.add(new RatedDocument("test", "testtype", "4", 4));
        PrecisionAtN precisionAtN = new PrecisionAtN(5);
        precisionAtN.setRelevantRatingThreshhold(2);
        EvalQueryQuality evaluated = precisionAtN.evaluate("id", toSearchHits(rated, "test", "testtype"), rated);
        assertEquals((double) 3 / 5, evaluated.getQualityLevel(), 0.00001);
        assertEquals(3, ((PrecisionAtN.Breakdown) evaluated.getMetricDetails()).getRelevantRetrieved());
        assertEquals(5, ((PrecisionAtN.Breakdown) evaluated.getMetricDetails()).getRetrieved());
    }

    public void testPrecisionAtFiveCorrectIndex() {
        List<RatedDocument> rated = new ArrayList<>();
        rated.add(new RatedDocument("test_other", "testtype", "0", Rating.RELEVANT.ordinal()));
        rated.add(new RatedDocument("test_other", "testtype", "1", Rating.RELEVANT.ordinal()));
        rated.add(new RatedDocument("test", "testtype", "0", Rating.RELEVANT.ordinal()));
        rated.add(new RatedDocument("test", "testtype", "1", Rating.RELEVANT.ordinal()));
        rated.add(new RatedDocument("test", "testtype", "2", Rating.IRRELEVANT.ordinal()));
        // the following search hits contain only the last three documents
        EvalQueryQuality evaluated = (new PrecisionAtN(5)).evaluate("id", toSearchHits(rated.subList(2, 5), "test", "testtype"), rated);
        assertEquals((double) 2 / 3, evaluated.getQualityLevel(), 0.00001);
        assertEquals(2, ((PrecisionAtN.Breakdown) evaluated.getMetricDetails()).getRelevantRetrieved());
        assertEquals(3, ((PrecisionAtN.Breakdown) evaluated.getMetricDetails()).getRetrieved());
    }

    public void testPrecisionAtFiveCorrectType() {
        List<RatedDocument> rated = new ArrayList<>();
        rated.add(new RatedDocument("test", "other_type", "0", Rating.RELEVANT.ordinal()));
        rated.add(new RatedDocument("test", "other_type", "1", Rating.RELEVANT.ordinal()));
        rated.add(new RatedDocument("test", "testtype", "0", Rating.RELEVANT.ordinal()));
        rated.add(new RatedDocument("test", "testtype", "1", Rating.RELEVANT.ordinal()));
        rated.add(new RatedDocument("test", "testtype", "2", Rating.IRRELEVANT.ordinal()));
        EvalQueryQuality evaluated = (new PrecisionAtN(5)).evaluate("id", toSearchHits(rated.subList(2, 5), "test", "testtype"), rated);
        assertEquals((double) 2 / 3, evaluated.getQualityLevel(), 0.00001);
        assertEquals(2, ((PrecisionAtN.Breakdown) evaluated.getMetricDetails()).getRelevantRetrieved());
        assertEquals(3, ((PrecisionAtN.Breakdown) evaluated.getMetricDetails()).getRetrieved());
    }

    public void testNoRatedDocs() throws Exception {
        List<RatedDocument> rated = new ArrayList<>();
        EvalQueryQuality evaluated = (new PrecisionAtN(5)).evaluate("id", toSearchHits(rated, "test", "testtype"), rated);
        assertEquals(0.0d, evaluated.getQualityLevel(), 0.00001);
        assertEquals(0, ((PrecisionAtN.Breakdown) evaluated.getMetricDetails()).getRelevantRetrieved());
        assertEquals(0, ((PrecisionAtN.Breakdown) evaluated.getMetricDetails()).getRetrieved());
    }

    public void testParseFromXContent() throws IOException {
        String xContent = " {\n"
         + "   \"size\": 10,\n"
         + "   \"relevant_rating_threshold\" : 2"
         + "}";
        XContentParser parser = XContentFactory.xContent(xContent).createParser(xContent);
        PrecisionAtN precicionAt = PrecisionAtN.fromXContent(parser, () -> ParseFieldMatcher.STRICT);
        assertEquals(10, precicionAt.getN());
        assertEquals(2, precicionAt.getRelevantRatingThreshold());
    }

    public void testCombine() {
        PrecisionAtN metric = new PrecisionAtN();
        Vector<EvalQueryQuality> partialResults = new Vector<>(3);
        partialResults.add(new EvalQueryQuality("a", 0.1));
        partialResults.add(new EvalQueryQuality("b", 0.2));
        partialResults.add(new EvalQueryQuality("c", 0.6));
        assertEquals(0.3, metric.combine(partialResults), Double.MIN_VALUE);
    }

    public static PrecisionAtN createTestItem() {
        int position = randomIntBetween(0, 1000);
        return new PrecisionAtN(position);
    }

    public void testXContentRoundtrip() throws IOException {
        PrecisionAtN testItem = createTestItem();
        XContentParser itemParser = RankEvalTestHelper.roundtrip(testItem);
        itemParser.nextToken();
        itemParser.nextToken();
        PrecisionAtN parsedItem = PrecisionAtN.fromXContent(itemParser, () -> ParseFieldMatcher.STRICT);
        assertNotSame(testItem, parsedItem);
        assertEquals(testItem, parsedItem);
        assertEquals(testItem.hashCode(), parsedItem.hashCode());
    }

    private static SearchHit[] toSearchHits(List<RatedDocument> rated, String index, String type) {
        InternalSearchHit[] hits = new InternalSearchHit[rated.size()];
        for (int i = 0; i < rated.size(); i++) {
            hits[i] = new InternalSearchHit(i, i+"", new Text(type), Collections.emptyMap());
            hits[i].shard(new SearchShardTarget("testnode", new Index(index, "uuid"), 0));
        }
        return hits;
    }
}

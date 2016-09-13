/*
 * The MIT License
 *
 * Copyright (c) 2016 Nils Homer
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package picard.analysis;

import htsjdk.samtools.SAMReadGroupRecord;
import htsjdk.samtools.metrics.MetricsFile;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.StringUtil;
import picard.PicardException;
import picard.cmdline.CommandLineProgramProperties;
import picard.cmdline.Option;
import picard.cmdline.programgroups.Alpha;
import picard.filter.CountingFilter;
import picard.filter.CountingPairedFilter;
import picard.util.RExecutor;

import java.io.File;
import java.util.List;

@CommandLineProgramProperties(
        usage = CollectWgsMetricsWithNonZeroCoverage.USAGE_SUMMARY + CollectWgsMetricsWithNonZeroCoverage.USAGE_DETAILS,
        usageShort = CollectWgsMetricsWithNonZeroCoverage.USAGE_SUMMARY,
        programGroup = Alpha.class
)
public class CollectWgsMetricsWithNonZeroCoverage extends CollectWgsMetrics {

    static final String USAGE_SUMMARY = "Collect metrics about coverage and performance of whole genome sequencing (WGS) experiments.  ";
    static final String USAGE_DETAILS = "This tool collects metrics about the percentages of reads that pass base- and mapping- quality " +
            "filters as well as coverage (read-depth) levels. Both minimum base- and mapping-quality values as well as the maximum " +
            "read depths (coverage cap) are user defined.  This extends CollectWgsMetrics by including metrics related only to sites" +
            "with non-zero (>0) coverage." +
            "<p>Note: Metrics labeled as percentages are actually expressed as fractions!</p>" +
            "<h4>Usage Example:</h4>" +
            "<pre>"  +
            "java -jar picard.jar CollectWgsMetricsWithNonZeroCoverage \\<br /> " +
            "      I=input.bam \\<br /> "+
            "      O=collect_wgs_metrics.txt \\<br /> " +
            "      CHART=collect_wgs_metrics.pdf  \\<br /> " +
            "      R=reference_sequence.fasta " +
            "</pre>" +
            "Please see the " +
            "<a href='https://broadinstitute.github.io/picard/picard-metric-definitions.html#CollectWgsMetricsWithNonZeroCoverage.WgsMetricsWithNonZeroCoverage'>" +
            "WgsMetricsWithNonZeroCoverage</a> documentation for detailed explanations of the output metrics." +
            "<hr />";

    @Option(shortName = "CHART", doc = "A file (with .pdf extension) to write the chart to.")
    public File CHART_OUTPUT;

    private final Log log = Log.getInstance(CollectWgsMetricsWithNonZeroCoverage.class);

    // Store this here since we need access to it in the doWork method
    private WgsMetricsWithNonZeroCoverageCollector collector = null;

    /** Metrics for evaluating the performance of whole genome sequencing experiments. */
    public static class WgsMetricsWithNonZeroCoverage extends WgsMetrics {
        public enum Category { WHOLE_GENOME, NON_ZERO_REGIONS }

        /** One of either WHOLE_GENOME or NON_ZERO_REGIONS */
        public Category CATEGORY;
    }

    public static void main(final String[] args) {
        new CollectWgsMetrics().instanceMainWithExit(args);
    }

    @Override
    protected int doWork() {
        IOUtil.assertFileIsWritable(CHART_OUTPUT);
        this.collector = new WgsMetricsWithNonZeroCoverageCollector(COVERAGE_CAP);
        super.doWork();

        return 0;
    }

    @Override
    protected WgsMetricsWithNonZeroCoverage generateWgsMetrics() {
        return new WgsMetricsWithNonZeroCoverage();
    }

    @Override
    protected WgsMetricsCollector getCollector(final int coverageCap) {
        assert(coverageCap == this.collector.coverageCap);
        return this.collector;
    }

    protected class WgsMetricsWithNonZeroCoverageCollector extends WgsMetricsCollector {
        public WgsMetricsWithNonZeroCoverageCollector(final int coverageCap) {
            super(coverageCap);
        }

        @Override
        public void addToMetricsFile(final MetricsFile<WgsMetrics, Integer> file,
                                     final boolean includeBQHistogram,
                                     final CountingFilter dupeFilter,
                                     final CountingFilter mapqFilter,
                                     final CountingPairedFilter pairFilter) {

            // calculate metrics the same way as in CollectWgsMetrics
            final WgsMetricsWithNonZeroCoverage metrics = (WgsMetricsWithNonZeroCoverage) getMetrics(dupeFilter, mapqFilter, pairFilter);
            metrics.CATEGORY = WgsMetricsWithNonZeroCoverage.Category.WHOLE_GENOME;

            // set count of the coverage-zero bin to 0 and re-calculate metrics
            highQualityDepthHistogramArray[0] = 0;
            unfilteredDepthHistogramArray[0] = 0;

            final WgsMetricsWithNonZeroCoverage metricsNonZero = (WgsMetricsWithNonZeroCoverage) getMetrics(dupeFilter, mapqFilter, pairFilter);
            metricsNonZero.CATEGORY = WgsMetricsWithNonZeroCoverage.Category.NON_ZERO_REGIONS;

            file.addMetric(metrics);
            file.addMetric(metricsNonZero);

            if (includeBQHistogram) {
                addBaseQHistogram(file);
            }

            file.addHistogram(getHighQualityDepthHistogram());
        }
    }

}
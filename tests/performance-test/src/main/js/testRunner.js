/*jslint node: true */

/*
 * #%L
 * %%
 * Copyright (C) 2017 BMW Car IT GmbH
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

var testbase = require("test-base");
var PerformanceUtilities = require("./performanceutilities.js");
var Promise = require("bluebird").Promise;

var error = testbase.logging.error;
var log = testbase.logging.log;
var options = PerformanceUtilities.getCommandLineOptionsOrDefaults(process.env);
var summary = [];
var benchmarks = null;
var Benchmarks = require("./Benchmarks");
var ProcessManager = require("./ProcessManager");

var testRunner = {
    displaySummary: function() {
        error("");
        error("Summary:");
        error("");
        summary.forEach(item => testRunner.logResults(item));
    },

    executeBenchmarks: function() {
        benchmarks = PerformanceUtilities.findBenchmarks(new Benchmarks(testRunner));
        return Promise.map(benchmarks, benchmark => testRunner.executeMultipleSubRuns(benchmark), {
            concurrency: 1
        }).then(testRunner.displaySummary);
    },

    executeSubRuns: function(benchmarkName, index) {
        var startTime;
        var numRuns = Number.parseInt(options.numRuns);

        return ProcessManager.proxy
            .prepareBenchmark(benchmarkName)
            .then(() => {
                ProcessManager.provider.startMeasurement();
                ProcessManager.proxy.startMeasurement();
                startTime = Date.now();
                return ProcessManager.proxy.executeBenchmark(benchmarkName);
            })
            .then(function() {
                var elapsedTimeMs = Date.now() - startTime;
                log(
                    benchmarkName +
                        " " +
                        index +
                        " runs: " +
                        numRuns +
                        " took " +
                        elapsedTimeMs +
                        " ms. " +
                        numRuns / (elapsedTimeMs / 1000) +
                        " msgs/s"
                );
                let providerMeasurementPromise = ProcessManager.provider.stopMeasurement();
                let proxyMeasurementPromise = ProcessManager.proxy.stopMeasurement();
                return Promise.all([providerMeasurementPromise, proxyMeasurementPromise]).then(values => {
                    return { proxy: values[1], provider: values[0], time: elapsedTimeMs };
                });
            });
    },

    executeMultipleSubRuns: function(benchmarkName) {
        console.log("executeMultipleSubRuns");
        var numRuns = options.numRuns;
        var testRuns = options.testRuns ? Number.parseInt(options.testRuns) : 1;
        var totalRuns = numRuns * testRuns;
        var totalLatency = 0;
        var testIndex = 0;
        var dummyArray = new Array(testRuns);
        var proxyUserTime = [];
        var proxySystemTime = [];
        var providerUserTime = [];
        var providerSystemTime = [];
        var latency = [];

        var measureMemory = options.measureMemory == "true";
        var memInterval;
        var memSum = 0;
        var memTests = 0;

        if (measureMemory) {
            memInterval = setInterval(function() {
                var memoryUsage = process.memoryUsage();
                memSum += memoryUsage.rss;
                memTests++;
            }, 1000);
        }

        return Promise.map(
            dummyArray,
            function() {
                testIndex++;
                return testRunner.executeSubRuns(benchmarkName, testIndex).then(function(result) {
                    totalLatency += result.time;
                    providerUserTime.push(result.provider.user);
                    providerSystemTime.push(result.provider.system);
                    proxyUserTime.push(result.proxy.user);
                    proxySystemTime.push(result.proxy.system);
                    latency.push(result.time);
                });
            },
            { concurrency: 1 }
        ).then(function() {
            totalLatency = latency.reduce((acc, curr) => acc + curr);
            var averageMsgPerSecond = totalRuns / (totalLatency / 1000);
            var variance = 0;
            var highestMsgPerSecond = -1;
            latency.map(time => numRuns / (time / 1000)).forEach(runMsgPerSecond => {
                variance += Math.pow(runMsgPerSecond - averageMsgPerSecond, 2);
                highestMsgPerSecond = Math.max(runMsgPerSecond, highestMsgPerSecond);
            });
            variance /= proxyUserTime.length;
            var deviation = Math.sqrt(variance).toFixed(2);
            highestMsgPerSecond = highestMsgPerSecond.toFixed(2);
            averageMsgPerSecond = averageMsgPerSecond.toFixed(2);

            var result = { time: {}, percentage: {} };

            result.other = {
                averageTime: averageMsgPerSecond,
                deviation,
                highestMsgPerSecond,
                benchmarkName,
                totalLatency
            };
            // cpu usage is in micro seconds -> divide by 1000
            result.time.totalProviderUserTime = providerUserTime.reduce((acc, curr) => acc + curr) / 1000.0;
            result.time.totalProviderSystemTime = providerSystemTime.reduce((acc, curr) => acc + curr) / 1000.0;
            result.time.totalProxyUserTime = proxyUserTime.reduce((acc, curr) => acc + curr) / 1000.0;
            result.time.totalProxySystemTime = proxySystemTime.reduce((acc, curr) => acc + curr) / 1000.0;
            result.time.totalProviderTime = result.time.totalProviderUserTime + result.time.totalProviderSystemTime;
            result.time.totalProxyTime = result.time.totalProxyUserTime + result.time.totalProxySystemTime;
            result.time.totalTime = result.time.totalProviderTime + result.time.totalProxyTime;

            result.percentage.providerPercentage = result.time.totalProviderTime / totalLatency;
            result.percentage.proxyPercentage = result.time.totalProxyTime / totalLatency;

            testRunner.logResults(result);

            if (measureMemory) {
                var averageMemory = memSum / memTests;
                var mb = (averageMemory / 1048576.0).toFixed(2);
                error("test used on average: " + mb + " MB memory");
                clearInterval(memInterval);
                result.other.mb = mb;
            }
            summary.push(result);
        });
    },
    logResults: function(result) {
        error("");
        error("Benchmark    : " + result.other.benchmarkName);
        error("total latency: " + result.other.totalLatency + " ms ");
        error("speed average: " + result.other.averageTime + " +/- " + result.other.deviation + " msgs/s: ");
        error("speed highest: " + result.other.highestMsgPerSecond + " msg/s");

        for (let key in result.time) {
            if (Object.prototype.hasOwnProperty.call(result.time, key)) {
                error(key + ": " + result.time[key].toFixed(0) + "ms");
            }
        }

        for (let key in result.percentage) {
            if (Object.prototype.hasOwnProperty.call(result.percentage, key)) {
                error(key + ": " + (result.percentage[key] * 100).toFixed(1) + "%");
            }
        }
    }
};
module.exports = testRunner;

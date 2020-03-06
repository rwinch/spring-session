GRADLE_PROFILER_VERSION=0.11.0
GRADLE_PROFILER_DOWNLOAD_DIR=build/gradle-profiler/download
GRADLE_VERSION_OR_LOCAL_INSTALL_PATH=6.3-20200304230105+0000

gp_install() {
	rm -rf build .instant-execution-state
	mkdir -p build/gradle-profiler/download
	wget "https://repo.gradle.org/gradle/ext-releases-local/org/gradle/profiler/gradle-profiler/$GRADLE_PROFILER_VERSION/gradle-profiler-$GRADLE_PROFILER_VERSION.zip" -P $GRADLE_PROFILER_DOWNLOAD_DIR
	unzip $GRADLE_PROFILER_DOWNLOAD_DIR/*.zip -d $GRADLE_PROFILER_DOWNLOAD_DIR
	export PATH="$PATH:$GRADLE_PROFILER_DOWNLOAD_DIR/gradle-profiler-$GRADLE_PROFILER_VERSION/bin"
}

gp_configurationTime() {
	gradle-profiler --gradle-version=$GRADLE_VERSION_OR_LOCAL_INSTALL_PATH --output-dir build/gradle-profiler/results/configurationTime --gradle-user-home build/gradle-profiler/gradle-user-home --scenario-file gradle/performance-scenarios/performance.scenario --measure-config-time --warmups 2 --iterations 10 --csv-format long --benchmark configurationTime
}

gp_benchmark() {
	gradle-profiler --gradle-version=$GRADLE_VERSION_OR_LOCAL_INSTALL_PATH --output-dir build/gradle-profiler/results/benchmark --gradle-user-home build/gradle-profiler/gradle-user-home --scenario-file gradle/performance-scenarios/performance.scenario --measure-config-time --warmups 2 --iterations 50 \
	--csv-format long \
	--benchmark \
noOptimizations onlyVfsRetention onlyInstantExecution allOptimizations

}

gp_profile() {
	gradle-profiler --gradle-version=$GRADLE_VERSION_OR_LOCAL_INSTALL_PATH \
		--output-dir build/gradle-profiler/results/profile --gradle-user-home build/gradle-profiler/gradle-user-home --scenario-file gradle/performance-scenarios/performance.scenario \
		--measure-config-time \
		--warmups 2 \
		--csv-format long \
		--profile async-profiler noOptimizations

}

gp_install
gp_configurationTime
gp_benchmark
gp_profile

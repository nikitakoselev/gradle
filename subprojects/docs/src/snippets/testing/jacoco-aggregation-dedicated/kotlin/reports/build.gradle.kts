plugins {
    id("jacoco")
}

dependencies {
    jacocoAggregation(project(":sub1"))
    jacocoAggregation(project(":sub2"))
}

tasks.register("aggregatedTestReport", JacocoAggregatedReport::class)

# LX-VSM-MAVEN

## What is it? 

This is a maven plugin to submit project information, including a [CycloneDX](https://cyclonedx.org/) compliant SBOM, to your [LeanIX VSM](https://www.leanix.net/en/products/value-stream-management) workspace via the [Service Discovery API](https://docs-vsm.leanix.net/reference/discovery_service).

## When should I use it?

Primarily used for submitting an SBOM during your CI/CD pipeline, but can be used to submit additional data during various stages of development and deployment, such as version numbers, testing results, code quality, and deployment times.

## How does it work?

`maven` is used to run this package. This script pulls some metadata from the `pom.xml` file and uses the arguments to authenticate with your workspace to submit this data and the (optional) SBOM to the [Service Discovery API](https://docs-vsm.leanix.net/reference/discovery_service). See the next section for detailed instructions.

## How do I use it?

1. Clone this repository to your computer
2. Run `mvn install` to install this utility to your local maven installation
3. Add the following to your `pom.xml` like this:
```xml
<project>
...
    <build>
        <plugins>
            <plugin>
                <groupId>org.leanix</groupId>
                <artifactId>lx-vsm-maven</artifactId>
                <version>1.0.0</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>lx-vsm-mvn</goal>
                        </goals>
                        <configuration>
                            <region>us</region>
                            <host>demo-us</host>
                            <apiToken>${vsmToken}</apiToken>
                        </configuration>
                    </execution>
                    </executions>
            </plugin>
        </plugins>
    </build>
...
    <properties>
        <vsmToken>${env.VSM_TOKEN}</vsmToken>
    </properties>
</project>
```
Note: This package also supports uploading CycloneDX SBOM files and can easily be paired with the [cyclonedx maven plugin](https://github.com/CycloneDX/cyclonedx-maven-plugin) with an additional plugin definition before this one:
```xml
<plugin>
    <groupId>org.cyclonedx</groupId>
    <artifactId>cyclonedx-maven-plugin</artifactId>
    <version>2.7.3</version>
</plugin>
```
4. Update the region, host, and vsmToken for your instance of VSM
5. Run `mvn org.leanix:lx-vsm-maven:lx-vsm-mvn` or `mvn package` to update your service in VSM

## License

This project is licensed under the MIT License

## Contact

Start with the [VSM Documentation](https://docs-vsm.leanix.net/docs), or feel free to contact [LeanIX Support](https://leanix.zendesk.com/hc/en-us/community/topics) for anything else.

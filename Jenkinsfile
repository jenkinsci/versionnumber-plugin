#!/usr/bin/groovy

buildPlugin(failFast: false,
            useContainerAgent: true,
            configurations: [
                [platform: 'linux',   jdk: '17'],
                [platform: 'linux',   jdk: '21', jenkins: '2.414'],
                [platform: 'linux',   jdk: '11']
            ])

# VersionNumber-PLUGIN

This plugin creates a new version number and stores it in the
environment variable whose name you specify in the configuration.

# Configuration

In many cases, the Jenkins build number isn't rich enough to express the
information you'd like the version number to have, and generating
externally (as part of the build) may not be an optimal solution. This
plugin allows you to generate a version number that contains much more
information.

## Project start date

The version number system has the concept of builds per day / week /
month / year / all time. Each of these is the calendar day / week /
month / year; that is, all builds in October will have the same build
month, all builds in 2009 will have the same build year. These are based
on the project start date, which is one of the user-configurable
options.

## Version Number Format String

The version number format string is processed to create the version
number that's stored in the named environment variable. Every character
in the version number format string is passed through to the final
version number, with the exception of variables enclosed in ${}. For
example, the version format string 1.0.${BUILDS\_THIS\_YEAR}, if this
were the 10th build this calendar year, would return 1.0.10.

The following are valid variables for use in the version number format
string:

| name                          | function                                                                                                                                                                                                                                                                                                                                                                                                        |
|-------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| BUILD\_DATE\_FORMATTED        | Takes the second argument and returns a java-formatted date string for the given build date. For example, ${BUILD\_DATE\_FORMATTED, "yyyy-MM-dd"} would return the date (and not the time) as something like 2009-10-01. The date format string must be surrounded by quotes, and any whitespace within the format string is significant.                                                                       |
| BUILD\_DAY                    | With no arguments, it just returns the day of the build as an integer. If there is an argument, it takes the number of characters in the argument and uses that pad the date string. For example, if it's the third of the month, ${BUILD\_DAY} would return 3, ${BUILD\_DAY, X} would return 3, and ${BUILD\_DAY, XX} would return 03.                                                                         |
| BUILD\_WEEK                   | Returns the week, with the same argument convention for BUILD\_DAY                                                                                                                                                                                                                                                                                                                                              |
| BUILD\_MONTH                  | Returns the month, with the same argument convention for BUILD\_DAY                                                                                                                                                                                                                                                                                                                                             |
| BUILD\_YEAR                   | Returns the year, with the same argument convention for BUILD\_DAY                                                                                                                                                                                                                                                                                                                                              |
| BUILDS\_TODAY                 | Returns the number of builds that have happened today, including this one. This resets at midnight. The argument convention is the same as for BUILD\_DAY                                                                                                                                                                                                                                                       |
| BUILDS\_THIS\_WEEK            | Returns the number of builds that have happened this week, including this one. This resets at the start of the week. The argument convention is the same as for BUILD\_DAY                                                                                                                                                                                                                                      |
| BUILDS\_THIS\_MONTH           | Returns the number of builds that have happened this month, including this one. This resets at the first of the month. The argument convention is the same as for BUILD\_DAY                                                                                                                                                                                                                                    |
| BUILDS\_THIS\_YEAR            | Returns the number of builds that have happened this year. This resets at the first of the year. The argument convention is the same as for BUILD\_DAY.                                                                                                                                                                                                                                                         |
| BUILDS\_ALL\_TIME             | Returns the number of builds that have happened since the project has begun. This is distinct from the hudson build number, in that it can be reset periodically (for example, when moving from 1.0.${BUILDS\_ALL\_TIME} to 2.0.${BUILDS\_ALL\_TIME}, and can be configured to start at an arbitrary number instead of the standard begin date.                                                                 |
| MONTHS\_SINCE\_PROJECT\_START | The number of months since the project start date. This is strictly dependent on the month of the current build and the month of the project start date; if the project was begun October 31st and the build was November 1st, then this would return 1. If the project was begin October 1st and the build was November 30th, this would also return 1. The argument convention is the same as for BUILD\_DAY. |
| YEARS\_SINCE\_PROJECT\_START  | The number of years since the project start date. Like MONTHS\_SINCE\_PROJECT\_START, this is dependent only on the year;                                                                                                                                                                                                                                                                                       |
| (anything else)               | Any other argument enclosed in ${} is replaced by an environment variable of the same name if one is available, or failing that, is just ignored. This can be used to integrate source control version numbers, for example.                                                                                                                                                                                    |

## Initialization Values

Before the build is started, the number of builds this year / month /
week / day can be specified on the command line or via the job's
plugin-configuration web-GUI. If they are specified, then they will
override whatever values are currently in production. This allows you to
migrate your version number from another system to Jenkins if you choose
to do so.

Additionally, it is possible to automatically override the number of
builds this year / month / week / day with values taken from
environment-variables. Instead of just providing a simple number in the
form-fields of the job's plugin-configuration which overrides the value
for the next build (as described above), you can instead provide an
environment-variable whose value will be extracted and used during the
next builds. If it is not set or its value is not convertible to a
positive integer (without loosing precision), the value of the previous
build will be taken instead and increased by one (as is the standard
behavior).

# Releases

## Version 1.9

-   Release Nov 17, 2017
-   Accepted [merge-request
    \#12](https://github.com/jenkinsci/versionnumber-plugin/pull/12). ("Adding
    functionality to limit the number of characters from a variable.")
-   Fixed
    bug [JENKINS-18171](https://issues.jenkins-ci.org/browse/JENKINS-18171). ("Version
    Number plugin doesn't increment build numbers after an unstable
    build.")  
    -   **NOTE:** This changes the checkbox for "skipping failed builds"
        (which actually meant to not increase the version-number if the
        former run was not successful) to a combobox of values.  
        The transition works smoothly and does not change the former
        behavior. However, **you should update the configuration of each
        job that had this checkbox checked (and save it)!  
        **(This assures that later updates of this plugin will not break
        your behavior due to this change.)

 

-   **IMPORTANT: **This might be the last update for this plugin in a
    long time, because I (user: bahadir) cannot maintain it any
    longer.**  
    Please volunteer to become the new maintainer of this plugin!**

## Version 1.8.1

-   Release Oct 11, 2016
-   Fixed
    bug [JENKINS-26729](https://issues.jenkins-ci.org/browse/JENKINS-26729). ("Endless
    loop when evaluating environment variables")

## Version 1.8

-   Release Oct 11, 2016
-   Pipeline-jobs now allow overriding values of BUILDS\_ALL\_TIME etc.
    by environment variables (or fixed values), too, similar to
    Freestyle-jobs.  
    (Use variables `overrideBuildsToday`, `overrideBuildsThisWeek`,
    `overrideBuildsThisMonth`, `overrideBuildsThisYear`,
    `overrideBuildsAllTime`.)
-   Fixed minor
    bug [JENKINS-15371](https://issues.jenkins-ci.org/browse/JENKINS-15371). ("Displayed
    Build version does not interpret parameters.")
-   Added minor logging.

## Version 1.7.2

-   Release Aug 22, 2016
-   Fixed a
    regression-bug. [JENKINS-36831](https://issues.jenkins-ci.org/browse/JENKINS-36831). ("Regression
    breakage in version number after upgrade.")

## Version 1.7.1

-   **IMPORTANT:** Lost in transaction. **Update to version 1.7.2!**

## Version 1.7

-   Release Jul 11, 2016
-   Added
    feature [JENKINS-34829](https://issues.jenkins-ci.org/browse/JENKINS-34829). ("Pipeline-Support
    for Version Number Plugin.")
-   Fixed some XSS-vulnerabilities.
-   Minor corrections / changes (of typos, formatting etc.).
-   Updated minimal required Jenkins versions to 1.625.3.
-   Updated minimal required Java-version to 7.
-   **IMPORTANT:** This version has a regression-bug. **Update to
    version 1.7.2!**

## Version 1.6

-   Release Oct 26, 2015
-   Support for BUILD\_WEEK and BUILDS\_THIS\_WEEK.
-   Added
    feature [JENKINS-29134](https://issues.jenkins-ci.org/browse/JENKINS-29134). ("Overriding
    values of BUILDS\_ALL\_TIME etc. by environment variables.")
-   Fixed issue
    [JENKINS-30224](https://issues.jenkins-ci.org/browse/JENKINS-30224). ("NPE
    thrown when a job uses an automatically installed JDK.")
-   Minor corrections / changes (of typos, formatting etc.).

## Version 1.4

-   Release Dec 17, 2011
-   Display name for every build can now be set to the formatted version
    number generated by this plugin.

## Version 1.3

-   Released Dec 21, 2009
-   Largely for compatibility reasons - was using rather old deprecated
    methods and wouldn't actually work with modern Hudson builds.
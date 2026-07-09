# Maintaining the tbmonitor spec file

## Read up on the source on how to build specs

https://www.thegeekstuff.com/2015/02/rpm-build-package-example/

[source]
----

sudo yum -y install rpm*

mkdir rpmbuild

cd rpmbuild

rpm-build

\# this will create the rpm directories below rpmbuild/{BUILD,BUILDROOT,RPMS,SOURCES,SPECS,SRPMS}

\#  another blog suggested creating a tmp folder, so I did


----

Copy the source of the tbmontor.spec file into the SPECS directory.

Get the latest AppTBMonitor...tar.gz artifact from apps.treasureboat.org/tbmonitor.

Update the tbmonitor.spec source to reflect the new build info on the artifact.

[source]
----

%prep

%setup -q -n AppTBMonitor-[3.6.0-SNAPSHOT-20200112-1421].woa


Update version and release number in the tbmonitor.spec to the next version.

[source]
----

Name: tbmonitor

Version: [1]

Release: [1]

## compile the spec

rpmbuild -ba ./SPECS/tbmonitor.spec

This will generate the tbmonitor.rpm in the RPMS folder with the Version-Release number attached.

Then we need to move the rpm to the apps.treasureboat.org/rpms folder so others can benefit.
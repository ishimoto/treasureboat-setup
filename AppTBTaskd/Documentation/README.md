# Maintaining the tbtaskd spec file

## Read up on the source on how to build specs

https://www.thegeekstuff.com/2015/02/rpm-build-package-example/

[source]
----

sudo yum -y install rpm*

mkdir rpmbuild

cd rpmbuild

rpmdev-setuptree

\# this will create the rpm directories below rpmbuild/{BUILD,BUILDROOT,RPMS,SOURCES,SPECS,SRPMS}

\#  another blog suggested creating a tmp folder, so I did



----

Copy the source of the tbmontor.spec file into the SPECS directory.

Get the latest Apptbtaskd...tar.gz artifact from apps.treasureboat.org/tbtaskd.

Update the tbtaskd.spec source to reflect the new build info on the artifact.

[source]
----

%prep

%setup -q -n AppTBTaskd-[3.6.0-SNAPSHOT-20200112-1421].woa


Update version and release number in the tbtaskd.spec to the next version.

[source]
----

Name: tbtaskd

Version: [1]

Release: [1]

## compile the spec

rpmbuild -ba ./SPECS/tbtaskd.spec

This will generate the tbtaskd.rpm in the RPMS folder with the Version-Release number attached.

Then we need to move the rpm to the apps.treasureboat.org/rpms folder so others can benefit.
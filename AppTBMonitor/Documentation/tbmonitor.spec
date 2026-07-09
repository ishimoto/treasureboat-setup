Name: tbmonitor
Version: %{_version}
Release: %{_release}
Summary: tbmonitor manage your TreasureBoat application instances.

Group: TreasureBoat Group/Deployment
License: TreasureBoat Public Software License
URL: http://apps.treasureboat.org/tbmonitor
Source: tbmonitor-Application.tar.gz
BuildArch: noarch
Vendor: TreasureBoat Community Association
Packager: Paul Yu <info@treasureboat.org>
BuildRoot: %{_builddir}/%{name}-buildroot

Prefix: /opt/TreasureBoat/Applications

%description
TreasureBoat Group Deployment uses tbmonitor to manage your tbtask instances.
The management is done by a Web-interface, running on port 56789. 

%prep
%setup -q -n AppTBMonitor-%{_BUILD_TAG}.woa

%install
rm -Rf %{buildroot}
mkdir -p %{buildroot}/opt/TreasureBoat/{Applications,Configuration,Logs}
mkdir -p %{buildroot}/etc/init.d/
%{__cp} -Rip $RPM_BUILD_DIR/AppTBMonitor-%{_BUILD_TAG}.woa %{buildroot}/opt/TreasureBoat/Applications/tbmonitor.woa
%{__cp} %{buildroot}/opt/TreasureBoat/Applications/tbmonitor.woa/Contents/Resources/tbmonitor2 %{buildroot}/etc/init.d/tbmonitor


%clean
rm -rf %{buildroot}

%pre
getent group appserveradm > /dev/null || groupadd -r appserveradm
getent passwd appserver > /dev/null || useradd -r -g appserveradm appserver

%post
chkconfig --add %{name}
chkconfig %{name} on
service %{name} start > /dev/null 2>&1

%preun
if [ "$1" = "0" ] ; then
service %{name} stop > /dev/null 2>&1
chkconfig --del %{name}
fi

%files
%defattr(-,appserver,appserveradm,-)
%dir /opt/TreasureBoat/Applications
%dir /opt/TreasureBoat/Logs
%dir /opt/TreasureBoat/Configuration
/opt/TreasureBoat/Applications/tbmonitor.woa
%attr(755,appserver,appserveradm) /opt/TreasureBoat/Applications/tbmonitor.woa/AppTBMonitor
%attr(755,appserver,appserveradm) /opt/TreasureBoat/Applications/tbmonitor.woa/Contents/MacOS/AppTBMonitor
%attr(755,root,wheel) /etc/init.d/tbmonitor

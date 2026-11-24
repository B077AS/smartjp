; Inno Setup script for the SmartJP installer (a branded wizard: welcome banner, install folder, desktop icon, start when done).
;
; It is a thin wrapper around the install folder the jpack Maven plugin builds (target/jpack/image/windows-x64: SmartJP.exe = the
; jpack stub, jpack.json, runtime/, app/, launcher/). Updating the app, the launcher, the Java runtime and the stub itself is done
; by the stub from then on, so this installer never has to run again. The release workflow calls it with:
;   /DAppVersion=...     the version shown in the installer and in Apps & features, such as 0.1.0
;   /DFileVersion=...    the same as digits only (1.2.3), for the file properties
;   /DImageDir=...       the jpack install folder to package
;   /DOutputDir=...      where to write the setup exe
;   /DIconFile=...       SmartJP's .ico
; The file is always called SmartJP-Setup.exe, so releases/latest/download/SmartJP-Setup.exe never changes.

#ifndef AppVersion
  #define AppVersion "0.0.0"
#endif
#ifndef FileVersion
  #define FileVersion "0.0.0"
#endif
#ifndef ImageDir
  #define ImageDir "..\..\target\jpack\image\windows-x64"
#endif
#ifndef OutputDir
  #define OutputDir "..\..\target\installer"
#endif
#ifndef IconFile
  #define IconFile "..\..\src\main\resources\icon.ico"
#endif

[Setup]
; Never change AppId: it is how a newer installer finds an installed SmartJP and upgrades it in place. The app writes the
; same value into its code (service/AppsFeatures.java) to keep the version in Apps & features current after a self-update.
AppId={{66F9B31F-876F-4B53-B25B-A9DCAF2D2B86}
AppName=SmartJP
AppVersion={#AppVersion}
AppVerName=SmartJP {#AppVersion}
AppPublisher=SmartJP
AppPublisherURL=https://github.com/B077AS/smartjp
AppSupportURL=https://github.com/B077AS/smartjp/issues
AppUpdatesURL=https://github.com/B077AS/smartjp/releases
VersionInfoVersion={#FileVersion}
VersionInfoDescription=SmartJP installer
; For this user only: no administrator prompt, and the folder stays writable for the stub's updates.
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
DefaultDirName={autopf}\SmartJP
DefaultGroupName=SmartJP
; A first install may pick the folder; an upgrade silently reuses the one SmartJP is already in.
DisableDirPage=auto
DisableProgramGroupPage=yes
DisableWelcomePage=no
WizardStyle=modern
WizardImageFile=wizard-large.bmp,wizard-large-150.bmp,wizard-large-200.bmp
WizardSmallImageFile=wizard-small.bmp,wizard-small-150.bmp,wizard-small-200.bmp
SetupIconFile={#IconFile}
UninstallDisplayIcon={app}\SmartJP.exe
UninstallDisplayName=SmartJP
OutputDir={#OutputDir}
OutputBaseFilename=SmartJP-Setup
Compression=lzma2
SolidCompression=yes
; SmartJP may be running when a newer setup is started: close it, install, offer to start it again.
CloseApplications=yes
RestartApplications=no

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[InstallDelete]
; Running the installer over an install that has updated itself starts from a clean slate: these folders are replaced as a whole,
; and a half-downloaded update or the folder a swap set aside must not outlive the files they belong to. Your data is untouched.
Type: filesandordirs; Name: "{app}\runtime"
Type: filesandordirs; Name: "{app}\app"
Type: filesandordirs; Name: "{app}\launcher"
Type: filesandordirs; Name: "{app}\update"
Type: filesandordirs; Name: "{app}\old"

[Files]
Source: "{#ImageDir}\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[Icons]
Name: "{group}\SmartJP"; Filename: "{app}\SmartJP.exe"
Name: "{autodesktop}\SmartJP"; Filename: "{app}\SmartJP.exe"; Tasks: desktopicon

[UninstallDelete]
; Updates add and replace files the installer never saw (a new runtime, jpack.log, the update and old folders, a stub that was renamed
; aside): remove the whole install folder, not just what Setup copied.
; Uninstalling removes the program, not your data: the database (saved lists, your own cards, the answer log, settings), the logs and the
; window's own data live in %LOCALAPPDATA%\smartjp, a different folder from the program, and are left alone. The access key of a
; server you synced with stays in the Windows credential store.
Type: filesandordirs; Name: "{app}"

[Run]
Filename: "{app}\SmartJP.exe"; Description: "{cm:LaunchProgram,SmartJP}"; Flags: nowait postinstall skipifsilent

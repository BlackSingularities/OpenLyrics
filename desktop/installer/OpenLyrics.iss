; Instalator OpenLyrics dla Windows (Inno Setup 6).
; Instalacja dla bieżącego użytkownika – bez uprawnień administratora, więc aktualizacja
; pobrana z redzikowski.com instaluje się po cichu i uruchamia aplikację ponownie.
#ifndef AppVersion
  #define AppVersion "1.0.0"
#endif
#ifndef NumVersion
  #define NumVersion "1.0.0"
#endif
#ifndef SourceDir
  #define SourceDir "..\build\compose\binaries\main\app\OpenLyrics"
#endif

[Setup]
AppId={{B2F4C1E6-7A3D-4E59-9C1B-5D8E2F6A4B73}
AppName=OpenLyrics
AppVersion={#AppVersion}
AppVerName=OpenLyrics {#AppVersion}
AppPublisher=Adam Rędzikowski
AppPublisherURL=https://redzikowski.com/portfolio/openlyrics
AppSupportURL=https://redzikowski.com
AppUpdatesURL=https://redzikowski.com/portfolio/openlyrics
DefaultDirName={localappdata}\Programs\OpenLyrics
DefaultGroupName=OpenLyrics
DisableProgramGroupPage=yes
DisableDirPage=auto
PrivilegesRequired=lowest
OutputDir=..\build\installer
OutputBaseFilename=OpenLyrics-{#AppVersion}-setup
SetupIconFile=..\icons\OpenLyrics.ico
UninstallDisplayIcon={app}\OpenLyrics.exe
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
CloseApplications=force
RestartApplications=no
VersionInfoVersion={#NumVersion}

[Languages]
Name: "polish"; MessagesFile: "compiler:Languages\Polish.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[InstallDelete]
; Stara wersja środowiska Javy nie może zostać obok nowej.
Type: filesandordirs; Name: "{app}\runtime"
Type: filesandordirs; Name: "{app}\app"

[Icons]
Name: "{group}\OpenLyrics"; Filename: "{app}\OpenLyrics.exe"
Name: "{autodesktop}\OpenLyrics"; Filename: "{app}\OpenLyrics.exe"; Tasks: desktopicon

[Registry]
; Autostart ustawiany w aplikacji – usuwany razem z nią.
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; ValueType: none; ValueName: "OpenLyrics"; Flags: uninsdeletevalue dontcreatekey

[Run]
Filename: "{app}\OpenLyrics.exe"; Description: "{cm:LaunchProgram,OpenLyrics}"; Flags: nowait postinstall skipifsilent
; Aktualizacja z aplikacji (tryb cichy) – uruchom nową wersję od razu.
Filename: "{app}\OpenLyrics.exe"; Flags: nowait; Check: WizardSilent

[UninstallRun]
Filename: "{cmd}"; Parameters: "/C taskkill /IM OpenLyricsMedia.exe /F"; Flags: runhidden; RunOnceId: "KillBridge"
Filename: "{cmd}"; Parameters: "/C taskkill /IM OpenLyrics.exe /F"; Flags: runhidden; RunOnceId: "KillApp"

[Code]
procedure CurStepChanged(CurStep: TSetupStep);
var
  Code: Integer;
begin
  // Mostek multimediów działa jako osobny proces – zamykamy go przed podmianą plików.
  if CurStep = ssInstall then
  begin
    Exec(ExpandConstant('{cmd}'), '/C taskkill /IM OpenLyricsMedia.exe /F', '', SW_HIDE, ewWaitUntilTerminated, Code);
    Exec(ExpandConstant('{cmd}'), '/C taskkill /IM OpenLyrics.exe /F', '', SW_HIDE, ewWaitUntilTerminated, Code);
    Sleep(500);
  end;
end;

# Notebook.Me v6.1.2

Feature-rich Java notebook app.

## Build the jar

Run:

```bat
build.bat
```

This creates `dist\\NotebookMe.jar`. Running that jar directly still requires Java on
the computer.

## Build the portable app

Run:

```bat
build-portable.bat
```

This creates `dist\NotebookMe-portable`. Copy that whole folder to another
Windows PC or USB drive and run `NotebookMe.bat`; Java does not need to be
installed on the target computer.

Portable notes and diary data are stored in `dist\NotebookMe-portable\data`.

Portable runtimes are operating-system specific. Build on macOS or Linux to
create a matching portable folder for those systems:

```sh
sh build-portable.sh
```

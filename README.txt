  INTRODUCTION

This plugin facilitates automatic review creation for each commit made on a
repository. Review creation can be enable/disabled on a per-project and
per-user basis.


  REQUIREMENTS

- FishEye _with_ Crucible (will not work with Crucible standalone)
- Release 2.1.0 or higher
- When running from the Plugin SDK, provide sufficient heap space:
  $ export ATLAS_OPTS=-Xmx512m
  $ atlas-run

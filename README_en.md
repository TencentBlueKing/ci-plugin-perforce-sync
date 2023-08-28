# Perforce Pull Plugin
Full implementation of p4 sync
## Plugin Features
- Supports Stream and classic repositories
- Supports automatic workspace creation
- Support password and ticket authentication methods
- Support HTTP proxy
- SSL is supported
- Unshelve is supported
- Support for file options
- Support p4 FileSpec and RevSpec
- Support Synchronous Parameter Configuration
- Supports concurrent synchronization and related parameter configuration
## Applicable Scenarios
- Need to synchronize files from the P4 server
- Do not want to install P4 environment locally, one-click synchronization

## Use Guide

### 1. Add Plugin

On the DevOps store in Blue Ocean->Workbench->Add Plugin page,

![addPlugin](images/addPlugin.png)

Fill in the field values as follows:

Name: PerforceSync (you can customize this)

atomCode: PerforceSync

Debug Project: Select your project

Development Language: Java

Custom Frontend: No

### 2. Configure Plugin

After the plugin is published, you can select the plugin in the pipeline, and the plugin is configured as follows:

![configPlugin](images/configPlugin.png)

- P4 Server Address: The address of the P4 server to be synchronized, supports SSL
- HTTP Proxy Address: If a proxy is needed, fill in the proxy address
- User Credentials: The credentials (username + password) filled in by Blue Ocean
- Character Set: The character encoding used during the pull process, Unicode needs to be enabled on the server
- File Save Path: The relative directory of the workspace
- Stream: Stream depot
- Manual: Non-stream depot, manually configure ViewMapping
- Unshelve Id: Change number of the shelve
- Synchronization File Versions: FileSpec and RevSpec supported by p4, one spec per line
- File Options: Some file options used for synchronization, Clobber is selected by default, otherwise updates may fail
- Sync Options: Synchronization options, note that some options conflict with each other and cannot be selected at the same time
- Concurrent Sync Options: Concurrent options used for synchronization (requires p4 server support for the --parallel parameter, old versions of the server may not support it)

## Common Failure Reasons and Solutions
* Ticket expired

  Refresh the ticket or issue a longer-ticket, such as a non-expiring ticket
* Invalid synchronization file versions format

  Refer to https://www.perforce.com/manuals/v20.2/cmdref/Content/CmdRef/p4_sync.html#p4_sync
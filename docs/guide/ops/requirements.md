---
title: Requirements
layout: website-normal
---

## Server Specification

The size of server required by Brooklyn depends on the amount of activity. This includes:

* the number of entities/VMs being managed
* the number of VMs being deployed concurrently
* the amount of management and monitoring required per entity

For dev/test or when there are only a handful of VMs being managed, a small VM is sufficient.
For example, an AWS m3.medium with one vCPU, 3.75GiB RAM and 4GB disk.

For larger production uses, a more appropriate machine spec would be two or more cores,
at least 8GB RAM and 20GB disk. The disk is just for logs, a small amount of persisted state, and
any binaries for custom blueprints/integrations.


## Supported Operating Systems

The recommended operating system is CentOS 6.x or RedHat 6.x.

Brooklyn has also been tested on Ubuntu 12.04 and OS X.


## Software Requirements

Brooklyn requires Java (JRE or JDK), version 6 or version 7. The most recent version 7 is recommended.
OpenJDK is recommended. Brooklyn has also been tested on IBM J9 and Oracle's JVM.

* check your `iptables` or other firewall service, making sure that incoming connections on port 8443 is not blocked
* check that the [linux kernel entropy](increase-entropy.html) is sufficient


## Configuration Requirements

### Ports

The ports used by Brooklyn are:

* 8443 for https, to expose the web-console and REST api.
* 8081 for http, to expose the web-console and REST api.

Whether to use https rather than http is configurable using the CLI option `--https`; 
the port to use is configurable using the CLI option `--port <port>`.
See [CLI](cli.html) documentation for more details.

To enable remote Brooklyn access, ensure these ports are open in the firewall.
For example, to open port 8443 in iptables, ues the command:

    /sbin/iptables -I INPUT -p TCP --dport 8443 -j ACCEPT


### Linux Kernel Entropy

Check that the [linux kernel entropy](increase-entropy.html) is sufficient.

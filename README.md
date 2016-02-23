## OpenStack plugin for Cloudera Director

# What is OpenStack Plugin for
OpenStack plugin is to let Cloudera Director be able to deploy and manage
clusters on a given OpenStack platform. By OpenStack plugin, the user can
customize his/her own environment configurations following his/her OpenStack
setup, and create Cloudera Manager instance as well as a Cloudera cluster with
this Cloudera Manager.

# Supported Features
Currently, OpenStack plugin can support below features:
1. Create Nova instances using the credentials and image/network/security
   groups assinged.
2. In a nova-network setup, allocate floating IPs from a specified floating IP
   pool, and associate to the specified instances.
3. Allocate cinder volumes, and attach them to specified instances.
4. Search or release resources by given IDs.

# Known Limitations
1. Users need to ensure each of his instances should be able to connect to the
   other instances via hostname, for instances in a Cloudera cluster is using
   hostnames to connect to other instances. This can be divided into two cases:
   * A genuine OpenStack setup using nova-network still has flaw in the hostname
     resolving. Instance can only recognize the hostnames of the other instances
     using the same DHCP service as itself, that is, on the same host as itself.
     In this case, users need to construct a global DNS service by himself to
     ensure all hostnames can be resolved and accessed from all instances.
   * A genuine OpenStack setup using Neutron can resolve all hostnames.
     However, there is still a bug in Neutron that the hostnames kept in the
     DNS server do not match the real hostname that each instance got. E.g.,
     when user creates an instance with a name "MyInst" and its private IP
     address is 10.0.0.2, then "MyInst" will be passed to this instance as its
     hostname. However, the hostname recorded in DNS server is generated from
     its allocated address as host-10-0-0-2.openstacklocal. To walk around this
     issue, the user needs to use a specified image, which can change its own
     hostname on boot to the value recorded in DNS server. This can be achieved
     by adding below lines to the end of /etc/rc.local file in the image.

```bash
MYIP=`ifconfig | awk '/inet addr/{print substr($2,6)}'`
array=(${MYIP// /\n})
add1=${array[0]}
hn=(${add1//\./-})
hostn=host-$hn.openstacklocal
hostname $hostn
```

2. This OpenStack plugin does not support Neuton floating IP APIs yet. So if
   the user is using Neutron, this plugin cannot support floating IP operations
   like allocating and associating. The private network still works. If the
   user wants to use Neutron + floating IP, he/she can create Cloudera Manager
   node and cluster without floating IP first, and associate floating IPs on
   the Cloud Provider dashboard.

# Platform Requirements
To use OpenStack plugin, the user needs to have an OpenStack setup, which is
capable of running the instances to be allocated for the Cloudera Manager and
roles in Cloudera cluster, as well as the volumes, floating IPs, and other
resources requried.

The instances allocated in the OpenStack setup should be able to access
internet (access via a proxy is ok), for Cloudera Director will download or
install files, parcels, and packages from internet. Without internet access,
this process will fail.

The user must ensure the security groups were already properly set, so that
the instances could be accessed via SSH, Cloudera Director dashboard, Cloudera
Manager dashboard. The ports for Cloudera Manager server, Cloudera Manager
agent, and other Big Data services should also be allowed in the security
groups for the corresponding roles.

# Prerequisites
1. An instance running Cloudera Director
   The user needs a server (could be a baremetal machine, or a virtual
   instance) to run Cloudera Director service. This server should be able
   to connect the instances in the OpenStack cloud by private IP addresses.
2. java-1.8.0-openjdk or later version is required on the Cloudera Director
   server to support OpenStack plugin.
3. The user should know below information about the OpenStack platform.
   * KeyStone endpoint, a URL like "http://172.16.5.20:5000/v2.0/" format.
   * User name
   * Tenant name
   * User password
   * Region name
   * Availability zone name (if exists)
   * Floating IP pool name
   * Private network ID
   * Image IDs for the Cloudera Manager instance and Cluster node instances
   * Keypair for the instances to be created
   * Security group names for the instances to be created
   * User names of the instances to be created. They are determined by the
     chosen images.

# How to Use OpenStack Plugin
Below is a quick start to tell how to deploy Cloudera Director, and 
1. Enable OpenStack Plugin
   * Download plugin source and compile the plugin by mvn. 
```bash
git clone https://github.com/cloudera/director-openstack-plugin/
cd director-openstack-plugin && mvn clean package
```
     The jar file will be generated at path
     director-openstack-plugin/target/openstack-1.0.0-SNAPSHOT.jar.
   * Upload the jar file to Cloudera Director server, and put it in directory
     /var/lib/cloudera-director-plugins/openstack-provider-1.0.0/openstack-provider-1.0.0.jar
   * Restart Cloudera Director service by run below command on Cloudera
     Director server.
```bash
sudo chown cloudera-director:cloudera-director /var/lib/cloudera-director-plugins/openstack-provider-1.0.0/openstack-provider-1.0.0.jar
sudo service cloudera-director-server restart
```

2. Login Cloudera Director Dashboard
   * Open a browser and input the Cloudera Director URL:
   http://<cloudera_director_ip>:7189/
   * Login with default user and password: admin/admin.

3. Add an User Environment
   * Click "Add Environment", and select "OpenStack" for "Cloud Provider".
   * Input the information required and click "Continue":
      ** Environment Name
      ** KeyStone Endpoint
      ** OpenStack Tenant Name
      ** OpenStack User Name
      ** OpenStack User Password
      ** Region
      ** Instance SSH User Name
      ** Instance SSH Private Key

4. Create Cloudera Manager Instance
   * In the environment page, click the pulldown menu, and select "Add Cloudera
     Manager".
   * Input Cloudera Manager name.
   * Click the pulldown menu to create a new instance template.
   * Put in the information required by instance template:
      ** Instance template name
      ** Instance flavor name (the selected flavor should at least have 8GB
         memory size, recommended >12GB)
      ** Image ID
      ** Security group names
      ** Network ID
      ** Keypair name
    * The user may also need to put in below additional information:
      ** Instance name prefix
      ** Availability zone
      ** Floating IP pool name
      ** volume number
      ** volume size
      ** SSH user name
      ** Bootstrap script
    * Save the template, and click "Continue" to create the Cloudera Manager
      instance. Wait until the process succeed and Cloudera Manager become
      ready.

5. Create Cloudera Cluster
   * In the environment page, click the pulldown menu, and select "Add Cluster".
   * Input cluster name.
   * Choose the services to be enabled. Here we just choose default
     "Core Hadoop".
   * Create instance templates for master, worker, and gateway, as we did for
     Cloudera Manager instance.
   * Choose the instance count for master, worker, and gateway. We suggest 1
     master, 1 gateway, and at least 3 workers.
   * Click "Continue" to create the cluster. Wait until the process succeed
     and Cloudera cluster become healthy.

6. Setup an Environment Using a Proxy to Connect to Internet
   If you are using an OpenStack setup in which instance access to internet
   should be through a proxy, you need to do below steps:
   * In the images for the instances, add "proxy=http://<proxy_host>:<port>" in
     file /etc/yum.conf, so that they can install packages from internet.
   * In the Cloudera Director server, add the proxy info to director service by
     below commands:
```bash
echo lp.proxy.http.host: <proxy_host> >> /etc/cloudera-director-server/application.properties
echo lp.proxy.http.port: <port> >> /etc/cloudera-director-server/application.properties
service cloudera-director-server restart
```
   * After step 4 and before step 5, login Cloudera Manager UI by URL
     http://<cloudera manager IP>:7180 and username/password as "admin/admin",
     and change the proxy setup by:
     ** Click Administration->Settings
     ** Search "proxy"
     ** Set "Proxy Server" and "Proxy Port" value to your proxy, and click "Save Changes"
     ** SSH into Cloudera Manager instance, and restart Cloudera Manager Server
        service by below commands:
```bash
sudo service cloudera-scm-server restart
```

# Fast Guide
1. How to setup a Cloudera Director server
   You can easily create a VM instance runing Cloudera Director service in
   your OpenStack platform from a fresh OS image. Here we choose CentOS6.7
   as an example. You can download the original image from 
   (http://cloud.centos.org/centos/6/images/CentOS-6-x86_64-GenericCloud-1509.qcow2)
   After use the image to launch the instance, SSH into the instance and su
   to root user to run below command lines:

```bash
# Set proxy if needed
# export http_proxy=http://<proxy_host>:<port>/
# export https_proxy=http://<proxy_host>:<port>/
# echo proxy=http://<proxy_host>:<port>/ >> /etc/yum.conf

yum update -y
yum install -y epel-release
# If access to internet via https is limited, replace the https to http in repo files.
# sed -i "s/https/http/g" /etc/yum.repos.d/*.repo
yum install -y nscd wget cloud-init cloud-utils cloud-utils-growpart dracut-modules-growroot
rpm -qa kernel | perl -pe 's/^kernel-//'  | xargs -I {} dracut -f /boot/initramfs-{}.img {}
touch /root/firstrun

wget -O /etc/yum.repos.d/cloudera-director.repo http://archive.cloudera.com/director/redhat/6/x86_64/director/cloudera-director.repo
wget -O /etc/yum.repos.d/cloudera-cdh5.repo http://archive.cloudera.com/cdh5/redhat/6/x86_64/cdh/cloudera-cdh5.repo
wget -O /etc/yum.repos.d/cloudera-manager.repo http://archive.cloudera.com/cm5/redhat/6/x86_64/cm/cloudera-manager.repo
# sed -i "s/https/http/g" /etc/yum.repos.d/*.repo
yum install -y java-1.8.0-openjdk cloudera-director-server cloudera-director-client
# Add proxy info in Cloudera Director configuration file.
# echo lp.proxy.http.host: <proxy_host> >> /etc/cloudera-director-server/application.properties
# echo lp.proxy.http.port: <port> >> /etc/cloudera-director-server/application.properties

service cloudera-director-server restart
```
   Now you can use this instance as the Cloudera Director server. You can also
   keep the instance snapshot as Cloudera Director image for future usage.

## Copyright and License
Copyright © 2016 Intel Corp. Licensed under the Apache License.

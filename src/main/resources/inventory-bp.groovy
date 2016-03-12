import java.util.ArrayList
import java.util.Iterator
import java.util.List
import java.util.Map.Entry
import com.branegy.inventory.api.ContactLinkService
import com.branegy.inventory.api.InventoryService
import com.branegy.inventory.model.Application
import com.branegy.inventory.model.ContactLink;
import com.branegy.inventory.model.Server
import com.branegy.inventory.model.Database
import com.branegy.service.base.api.ProjectService
import com.branegy.service.core.QueryRequest

def toURL = { link -> link.encodeURL().replaceAll("\\+", "%20") }
String.metaClass.encodeURL = { java.net.URLEncoder.encode(delegate) }

inventoryService = dbm.getService(InventoryService.class);
String projectName =  dbm.getService(ProjectService.class).getCurrentProject().getName();

def linkToObject = { object ->
    prefix = "#inventory/project:${toURL(projectName)}"
    if (object instanceof Application) {
        return "${prefix}/applications/application:${toURL(object.getApplicationName())}"
    } else if (object instanceof Server) {
        return  "${prefix}/servers/server:${toURL(object.getServerName())}"
    } else if (object instanceof Database) {
        return  "${prefix}/databases/server:${toURL(object.getServerName())},db:${toURL(object.getDatabaseName())}"
    } else {
        throw new RuntimeException("object of class ${object.getClass().getName()} wasn't expected")
    }
}


// Applications without contacts
// -------------------------------------------------------------------------------------------------------
def applicationsWithoutContacts = { applications ->
    def contactLinks = dbm.getService(ContactLinkService.class).findAllByClass(Application.class,null)
    appContacts = contactLinks.groupBy { it.getApplication() }
    return applications
            .collect{ it }
            .findAll { app -> appContacts[app]==null }
            .collect { [ "object_type" : "Application",
                         "object_name" : it.getApplicationName(),
                         "object_link" : linkToObject(it)+"/contacts",
                         "severity"    : "warning",
                         "description" : "Application '${it.getApplicationName()}' has no related contacts"
                       ]}
}

// Servers without contacts
// -------------------------------------------------------------------------------------------------------
def serversWithoutContacts = { servers ->
    def contactLinks = dbm.getService(ContactLinkService.class).findAllByClass(Server.class,null)
    serverContacts = contactLinks.groupBy { it.getServer() }
    return servers
            .collect{ it }
            .findAll { server -> serverContacts[server]==null }
            .collect { [ "object_type" : "Server",
                         "object_name" : it.getServerName(),
                         "object_link" : linkToObject(it)+"/contacts",
                         "severity"   : "warning",
                         "description" : "Server ${it.getServerName()} has no related contacts"
                       ]}
}

// Databases without applications
// -------------------------------------------------------------------------------------------------------
def dbName = { db -> db.getServerName() + "." + db.getDatabaseName() }

def databaseWithoutApplications = { databases ->
    def db2AppsLinks = dbm.getService(InventoryService.class).getDBUsageList();
    dbApps = db2AppsLinks.groupBy { it.getDatabase() }
    return databases
            .collect{ it }
             // TODO have to filter out "system" databases
            .findAll { db -> dbApps[db]==null && !db.isDeleted() }
            .collect { [ "object_type" : "Database",
                         "object_name" : dbName(it),
                         "object_link" : linkToObject(it)+"/applications",
                         "severity"   : "warning",
                         "description" : "Database ${dbName(it)} has no related applications"
                       ]}
}


// -----------------Rules---------------------------------------------------------------------------
service = dbm.getService(InventoryService.class)
def all = new QueryRequest()

applications = service.getApplicationList(all)
servers = service.getServerList(all)
databases = service.getDatabaseList(all)

def issues = applicationsWithoutContacts (applications)
issues.addAll(serversWithoutContacts(servers))
issues.addAll(databaseWithoutApplications(databases))

// -----------------Layout -------------------------------------------------------------------------

issues.sort { it -> it.object_type + it.object_name }
println  """<table class="simple-table" cellspacing="0" cellpadding="10">
            <tr style="background-color:#EEE">
               <td>Object Type</td><td>Object Name</td><td>Severity</td><td>Issue</td>
            </tr>"""
issues.each { issue ->
                 println """<tr><td>${issue.object_type}</td>
                                <td><a href="${issue.object_link}">${issue.object_name}</a></td>
                                <td>${issue.severity}</td>
                                <td>${issue.description}</td></tr>"""
            }
println "</table>"

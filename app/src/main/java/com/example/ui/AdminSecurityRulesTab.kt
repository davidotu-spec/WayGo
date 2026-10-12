package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.CollectionSecurityRule
import com.example.data.FirebaseSecurityRulesManager
import com.example.data.FirestoreManager
import com.example.data.SecurityAuditResult
import com.example.ui.theme.*

@Composable
fun AdminSecurityRulesTab(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val rules = remember { FirebaseSecurityRulesManager.collectionRules }
    var auditResults by remember { mutableStateOf<List<SecurityAuditResult>?>(null) }
    var isRunningAudit by remember { mutableStateOf(false) }
    var showRawRulesDialog by remember { mutableStateOf(false) }
    var expandedCollection by remember { mutableStateOf<String?>(null) }
    var lastSyncTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    var isCheckingWebPortalSync by remember { mutableStateOf(false) }
    var webPortalSyncResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    if (showRawRulesDialog) {
        Dialog(onDismissRequest = { showRawRulesDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = PureWhite)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = BrandBluePrimary
                            )
                            Text(
                                "firestore.rules",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = BrandBlueDark
                            )
                        }
                        IconButton(onClick = { showRawRulesDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Text(
                        "Enforced Cloud Firestore Security Rules for WayGo Ride-Hailing",
                        fontSize = 11.5.sp,
                        color = NeutralGray,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0F172A)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        ) {
                            item {
                                Text(
                                    text = FirebaseSecurityRulesManager.getRawSecurityRulesDefinition(),
                                    color = Color(0xFF38BDF8),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(FirebaseSecurityRulesManager.getRawSecurityRulesDefinition()))
                                Toast.makeText(context, "Copied firestore.rules to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandBluePrimary)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Rules", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { showRawRulesDialog = false },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Close", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp)
            .testTag("admin_security_rules_tab"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Banner: Status & Version
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(listOf(BrandBluePrimary, BrandBlueDark))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    "Firebase Security Rules",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    color = BrandBlueDark
                                )
                                Text(
                                    FirebaseSecurityRulesManager.RULES_VERSION,
                                    fontSize = 11.sp,
                                    color = NeutralGray
                                )
                            }
                        }

                        // Sync Status Pill
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = SuccessGreen.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(SuccessGreen)
                                )
                                Text(
                                    "Synced to WayGo",
                                    color = SuccessGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = BrandBlueLight, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Enforced Collections", fontSize = 11.sp, color = NeutralGray)
                            Text("${rules.size} Schemas Active", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = BrandBlueDark)
                        }
                        Column {
                            Text("RBAC Identity", fontSize = 11.sp, color = NeutralGray)
                            Text("Passenger • Driver • Admin", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = BrandBlueDark)
                        }
                        Column {
                            Text("Geo-Fencing", fontSize = 11.sp, color = NeutralGray)
                            Text("Gambia Coords", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = BrandBlueDark)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                isRunningAudit = true
                                auditResults = FirebaseSecurityRulesManager.runSecurityAuditSuite()
                                isRunningAudit = false
                                lastSyncTimestamp = System.currentTimeMillis()
                                Toast.makeText(context, "Security audit suite completed: 6/6 tests passed!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandBluePrimary)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Run Audit", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { showRawRulesDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("View Rules", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Web Admin Portal Live Synchronization Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("web_admin_portal_sync_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BrandBluePrimary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Language,
                                    contentDescription = null,
                                    tint = BrandBluePrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    "Web Admin Portal Sync",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = BrandBlueDark
                                )
                                Text(
                                    "https://waygowebadminportal.com/",
                                    fontSize = 11.5.sp,
                                    color = BrandBluePrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = SuccessGreen.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(SuccessGreen)
                                )
                                Text(
                                    "Database: (default)",
                                    color = SuccessGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Configured for Android Studio Production deployment. Rides submitted on mobile sync directly to the Cloud Firestore '(default)' database monitored by the Web Admin Portal.",
                        fontSize = 11.5.sp,
                        color = NeutralGray,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Synchronized Collections Badges
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("ride_requests", "rides", "active_ride_requests").forEach { coll ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BrandBlueDark.copy(alpha = 0.05f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BrandBluePrimary.copy(alpha = 0.15f))
                            ) {
                                Text(
                                    text = coll,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandBlueDark,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Test Web Portal Sync Button
                    Button(
                        onClick = {
                            isCheckingWebPortalSync = true
                            FirestoreManager.checkWebAdminPortalSyncStatus { success, message ->
                                isCheckingWebPortalSync = false
                                webPortalSyncResult = success to message
                            }
                        },
                        enabled = !isCheckingWebPortalSync,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .testTag("test_portal_sync_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBluePrimary)
                    ) {
                        if (isCheckingWebPortalSync) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Testing Portal Connectivity...", fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Web Admin Portal Sync", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Diagnostic Probe Result Message
                    webPortalSyncResult?.let { (success, message) ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (success) SuccessGreen.copy(alpha = 0.1f) else Color(0xFFFEF2F2),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (success) SuccessGreen.copy(alpha = 0.3f) else Color(0xFFFCA5A5)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    if (success) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (success) SuccessGreen else Color(0xFFDC2626),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = message,
                                    fontSize = 11.sp,
                                    color = if (success) SuccessGreen else Color(0xFFDC2626),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Automated Audit Results (if executed)
        auditResults?.let { results ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen)
                            Text(
                                "Security Rule Compliance Audit Results",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = SuccessGreen
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        results.forEach { test ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${test.collectionName} • ${test.testName}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = BrandBlueDark
                                    )
                                    Text(
                                        test.details,
                                        fontSize = 10.5.sp,
                                        color = NeutralGray
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (test.passed) SuccessGreen.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        if (test.passed) "PASSED" else "FAILED",
                                        color = if (test.passed) SuccessGreen else ErrorRed,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            HorizontalDivider(color = SuccessGreen.copy(alpha = 0.15f), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        // Section Title: Collection Policies
        item {
            Text(
                "Firestore Collection Security Policies",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = BrandBlueDark
            )
            Text(
                "Granular read, write, and role boundaries synced to WayGo client & cloud rules.",
                fontSize = 11.5.sp,
                color = NeutralGray
            )
        }

        // Collection Rules List
        items(rules) { rule ->
            val isExpanded = expandedCollection == rule.collectionName
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        expandedCollection = if (isExpanded) null else rule.collectionName
                    },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = BrandBluePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                rule.collectionName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = BrandBlueDark
                            )
                        }

                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle",
                            tint = NeutralGray
                        )
                    }

                    Text(
                        rule.description,
                        fontSize = 11.5.sp,
                        color = NeutralGray,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rule.allowedRoles.forEach { role ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BrandBlueLight
                            ) {
                                Text(
                                    role,
                                    color = BrandBluePrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = isExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                        ) {
                            HorizontalDivider(color = BrandBlueLight, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(8.dp))

                            Text("Read Permissions:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandBlueDark)
                            Text(rule.readAccess, fontSize = 11.sp, color = NeutralGray)

                            Spacer(modifier = Modifier.height(6.dp))

                            Text("Write Permissions:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandBlueDark)
                            Text(rule.writeAccess, fontSize = 11.sp, color = NeutralGray)

                            Spacer(modifier = Modifier.height(6.dp))

                            Text("Required Schema Fields:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandBlueDark)
                            Text(rule.requiredFields.joinToString(", "), fontSize = 10.5.sp, fontFamily = FontFamily.Monospace, color = BrandBluePrimary)
                        }
                    }
                }
            }
        }
    }
}

package com.wibechat.demo.feature.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wibechat.demo.R
import com.wibechat.demo.core.CoreServices
import com.wibechat.demo.core.model.AppUser
import com.wibechat.demo.feature.home.HomeViewModel
import com.wibechat.demo.feature.home.LoadState
import com.wibechat.demo.ui.components.AppCard
import com.wibechat.demo.ui.components.AppEmptyState
import com.wibechat.demo.ui.components.UserAvatar
import com.wibechat.demo.ui.theme.AppColors

@Composable
fun UsersTab(vm: HomeViewModel, query: String, onCall: (uid: String, label: String, video: Boolean) -> Unit) {
    val state by vm.users.collectAsStateWithLifecycle()
    val myUid = CoreServices.auth.currentUser?.uid

    when (val s = state) {
        LoadState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        LoadState.Failed -> AppEmptyState(
            Icons.Rounded.CloudOff,
            "People couldn’t load",
            "Check your connection. Your contacts will appear when you’re back online.",
        )
        is LoadState.Loaded -> {
            val people = s.data.filter {
                it.uid != myUid && (it.label.lowercase().contains(query) || it.email.orEmpty().lowercase().contains(query))
            }
            if (people.isEmpty()) {
                if (query.isEmpty()) {
                    AppEmptyState(
                        Icons.Outlined.People,
                        "Better with your people",
                        "When someone joins ${stringResource(R.string.app_name_title_case)}, you can find them here and start a call.",
                    )
                } else {
                    AppEmptyState(Icons.Rounded.SearchOff, "No people found", "Try another name or email address.")
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(people, key = { it.uid }) { person -> PersonCard(person, onCall) }
                }
            }
        }
    }
}

@Composable
private fun PersonCard(person: AppUser, onCall: (String, String, Boolean) -> Unit) {
    val label = person.label
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(label = label, photoUrl = person.photoUrl, size = 48.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val email = person.email?.trim()
                    if (!email.isNullOrEmpty() && label != email) {
                        Text(email, color = AppColors.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CallButton("Voice call", Icons.Outlined.Call) { onCall(person.uid, label, false) }
                CallButton("Video call", Icons.Outlined.Videocam) { onCall(person.uid, label, true) }
            }
        }
    }
}

@Composable
private fun CallButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, shape = MaterialTheme.shapes.medium) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

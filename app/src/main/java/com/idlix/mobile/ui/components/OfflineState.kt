package com.idlix.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idlix.mobile.R
import com.idlix.mobile.ui.theme.IdlixRed
import com.idlix.mobile.ui.theme.TextMuted
import com.idlix.mobile.ui.theme.TextWhite

/**
 * Single offline pattern used by Home / Movies / Series / Search: cloud-off
 * illustration, one localized title + message, and a Retry CTA.
 */
@Composable
fun OfflineState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.CloudOff,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.offline_title),
            color = TextWhite,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = message ?: stringResource(R.string.offline_message),
            color = TextMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.heightIn(min = 48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = IdlixRed)
        ) {
            Text(text = stringResource(R.string.try_again))
        }
    }
}

/**
 * Single empty-results pattern for grids: illustration, one localized title +
 * hint, and a Retry CTA that re-runs the load.
 */
@Composable
fun EmptyTitlesState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.SearchOff,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.no_titles_found),
            color = TextWhite,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.no_titles_desc),
            color = TextMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.heightIn(min = 48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = IdlixRed)
        ) {
            Text(text = stringResource(R.string.try_again))
        }
    }
}

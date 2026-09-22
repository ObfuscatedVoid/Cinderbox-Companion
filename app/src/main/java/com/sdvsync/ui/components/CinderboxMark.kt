package com.sdvsync.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.sdvsync.R

@Composable
fun CinderboxMark(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.ic_cinderbox_mark), contentDescription = null, modifier = modifier)
}

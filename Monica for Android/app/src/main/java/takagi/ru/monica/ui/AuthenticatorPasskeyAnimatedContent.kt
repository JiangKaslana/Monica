package takagi.ru.monica.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import takagi.ru.monica.ui.main.navigation.BottomNavItem
import takagi.ru.monica.ui.navigation.parallaxEnterFromLeft
import takagi.ru.monica.ui.navigation.parallaxExitToLeft
import takagi.ru.monica.ui.navigation.slideInFromRight
import takagi.ru.monica.ui.navigation.slideOutToRight

@Composable
internal fun AuthenticatorPasskeyAnimatedContent(
    currentTab: BottomNavItem,
    modifier: Modifier = Modifier,
    content: @Composable (BottomNavItem) -> Unit
) {
    val saveableStateHolder = rememberSaveableStateHolder()

    AnimatedContent(
        targetState = currentTab,
        modifier = modifier,
        transitionSpec = {
            val transform = when {
                initialState == BottomNavItem.Authenticator && targetState == BottomNavItem.Passkey ->
                    slideInFromRight() togetherWith parallaxExitToLeft()

                initialState == BottomNavItem.Passkey && targetState == BottomNavItem.Authenticator ->
                    parallaxEnterFromLeft() togetherWith slideOutToRight()

                else -> EnterTransition.None togetherWith ExitTransition.None
            }
            transform.using(SizeTransform(clip = false))
        },
        contentKey = BottomNavItem::key,
        label = "authenticator_passkey_switch",
        content = { targetTab ->
            saveableStateHolder.SaveableStateProvider(targetTab.key) {
                content(targetTab)
            }
        }
    )
}

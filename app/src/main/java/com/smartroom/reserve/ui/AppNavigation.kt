package com.smartroom.reserve.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import android.net.Uri
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.smartroom.reserve.data.User
import com.smartroom.reserve.ui.screens.AdminScreen
import com.smartroom.reserve.ui.screens.AssistantScreen
import com.smartroom.reserve.ui.screens.AuthFlow
import com.smartroom.reserve.ui.screens.BookingScreen
import com.smartroom.reserve.ui.screens.FindRoomScreen
import com.smartroom.reserve.ui.screens.HomeScreen
import com.smartroom.reserve.ui.screens.MyBookingsScreen
import com.smartroom.reserve.ui.screens.ProfileScreen
import com.smartroom.reserve.ui.screens.RoomDetailScreen

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

@Composable
fun AppRoot(vm: AppViewModel) {
    val ready by vm.ready.collectAsState()
    val user by vm.user.collectAsState()
    val current = user
    when {
        !ready -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        current == null -> AuthFlow(vm)
        // key() gives every login a fresh navigation stack
        else -> androidx.compose.runtime.key(current.id) { MainFlow(vm, current) }
    }
}

private fun NavHostController.goToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun MainFlow(vm: AppViewModel, user: User) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    val tabs = remember(user.isAdmin) {
        buildList {
            add(TabItem("home", "Rooms", Icons.Filled.Home))
            add(TabItem("ai", "AI", Icons.Filled.Star))
            add(TabItem("find", "Find", Icons.Filled.Search))
            add(TabItem("bookings", "Bookings", Icons.Filled.DateRange))
            if (user.isAdmin) add(TabItem("admin", "Admin", Icons.Filled.Settings))
            add(TabItem("profile", "Profile", Icons.Filled.Person))
        }
    }

    val snackbar = remember { SnackbarHostState() }
    val message by vm.message.collectAsState()
    LaunchedEffect(message) {
        val m = message
        if (m != null) {
            vm.clearMessage()
            snackbar.showSnackbar(m)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { nav.goToTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") {
                HomeScreen(vm, onRoom = { id -> nav.navigate("room/$id") })
            }
            composable("find") {
                FindRoomScreen(vm, onBook = { roomId, start, end, attendees ->
                    nav.navigate("book/$roomId?start=$start&end=$end&att=$attendees")
                })
            }
            composable("ai") {
                AssistantScreen(vm, onBook = { roomId, start, end, attendees, purpose ->
                    nav.navigate("book/$roomId?start=$start&end=$end&att=$attendees&purpose=${Uri.encode(purpose)}")
                })
            }
            composable("bookings") { MyBookingsScreen(vm) }
            composable("admin") { AdminScreen(vm) }
            composable("profile") { ProfileScreen(vm, user) }
            composable(
                "room/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                RoomDetailScreen(
                    vm, id,
                    onBack = { nav.popBackStack() },
                    onBook = { roomId, start, end -> nav.navigate("book/$roomId?start=$start&end=$end") }
                )
            }
            composable(
                "book/{id}?start={start}&end={end}&att={att}&purpose={purpose}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("start") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("end") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("att") { type = NavType.IntType; defaultValue = 1 },
                    navArgument("purpose") { type = NavType.StringType; defaultValue = "" }
                )
            ) { entry ->
                val args = entry.arguments
                BookingScreen(
                    vm = vm,
                    roomId = args?.getLong("id") ?: 0L,
                    initialStart = args?.getLong("start") ?: 0L,
                    initialEnd = args?.getLong("end") ?: 0L,
                    initialAttendees = args?.getInt("att") ?: 1,
                    initialPurpose = args?.getString("purpose") ?: "",
                    onBack = { nav.popBackStack() },
                    onDone = {
                        nav.navigate("bookings") {
                            popUpTo(nav.graph.findStartDestination().id)
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    }
}

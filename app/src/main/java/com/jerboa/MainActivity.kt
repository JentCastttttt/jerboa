package com.jerboa

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import arrow.core.Either
import com.jerboa.db.AccountRepository
import com.jerboa.db.AccountViewModel
import com.jerboa.db.AccountViewModelFactory
import com.jerboa.db.AppDB
import com.jerboa.ui.components.comment.edit.CommentEditActivity
import com.jerboa.ui.components.comment.edit.CommentEditViewModel
import com.jerboa.ui.components.comment.reply.CommentReplyActivity
import com.jerboa.ui.components.comment.reply.CommentReplyViewModel
import com.jerboa.ui.components.common.getCurrentAccount
import com.jerboa.ui.components.community.CommunityActivity
import com.jerboa.ui.components.community.CommunityViewModel
import com.jerboa.ui.components.community.list.CommunityListActivity
import com.jerboa.ui.components.community.list.CommunityListViewModel
import com.jerboa.ui.components.community.sidebar.CommunitySidebarActivity
import com.jerboa.ui.components.home.*
import com.jerboa.ui.components.inbox.InboxActivity
import com.jerboa.ui.components.inbox.InboxViewModel
import com.jerboa.ui.components.login.LoginActivity
import com.jerboa.ui.components.login.LoginViewModel
import com.jerboa.ui.components.person.PersonProfileActivity
import com.jerboa.ui.components.person.PersonProfileViewModel
import com.jerboa.ui.components.post.PostActivity
import com.jerboa.ui.components.post.PostViewModel
import com.jerboa.ui.components.post.create.CreatePostActivity
import com.jerboa.ui.components.post.create.CreatePostViewModel
import com.jerboa.ui.components.post.edit.PostEditActivity
import com.jerboa.ui.components.post.edit.PostEditViewModel
import com.jerboa.ui.components.privatemessage.PrivateMessageReplyActivity
import com.jerboa.ui.components.report.CreateReportViewModel
import com.jerboa.ui.components.report.comment.CreateCommentReportActivity
import com.jerboa.ui.components.report.post.CreatePostReportActivity
import com.jerboa.ui.components.settings.SettingsActivity
import com.jerboa.ui.components.settings.SettingsViewModel
import com.jerboa.ui.theme.JerboaTheme

class JerboaApplication : Application() {
    private val database by lazy { AppDB.getDatabase(this) }
    val repository by lazy { AccountRepository(database.accountDao()) }
}

class MainActivity : ComponentActivity() {

    private val homeViewModel by viewModels<HomeViewModel>()
    private val postViewModel by viewModels<PostViewModel>()
    private val loginViewModel by viewModels<LoginViewModel>()
    private val siteViewModel by viewModels<SiteViewModel>()
    private val communityViewModel by viewModels<CommunityViewModel>()
    private val personProfileViewModel by viewModels<PersonProfileViewModel>()
    private val inboxViewModel by viewModels<InboxViewModel>()
    private val communityListViewModel by viewModels<CommunityListViewModel>()
    private val createPostViewModel by viewModels<CreatePostViewModel>()
    private val commentReplyViewModel by viewModels<CommentReplyViewModel>()
    private val commentEditViewModel by viewModels<CommentEditViewModel>()
    private val postEditViewModel by viewModels<PostEditViewModel>()
    private val createReportViewModel by viewModels<CreateReportViewModel>()
    private val settingsViewModel by viewModels<SettingsViewModel>()

    private val accountViewModel: AccountViewModel by viewModels {
        AccountViewModelFactory((application as JerboaApplication).repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO this is bad
        val account = getCurrentAccount(accountViewModel.allAccountSync)
        fetchInitialData(account, siteViewModel, homeViewModel)

        setContent {
            JerboaTheme {
                val navController = rememberNavController()
                val ctx = LocalContext.current

                NavHost(
                    navController = navController,
                    startDestination = "splashScreen"
                ) {
                    composable(
                        route = "login",
                        deepLinks = DEFAULT_LEMMY_INSTANCES.map { instance ->
                            navDeepLink { uriPattern = "$instance/login" }
                        }
                    ) {
                        LoginActivity(
                            navController = navController,
                            loginViewModel = loginViewModel,
                            accountViewModel = accountViewModel,
                            siteViewModel = siteViewModel,
                            homeViewModel = homeViewModel
                        )
                    }
                    composable(route = "splashScreen") {
                        SplashScreenActivity(
                            navController = navController
                        )
                    }
                    composable(
                        route = "home",
                        deepLinks = DEFAULT_LEMMY_INSTANCES.map { instance ->
                            navDeepLink { uriPattern = instance }
                        }
                    ) {
                        HomeActivity(
                            navController = navController,
                            homeViewModel = homeViewModel,
                            accountViewModel = accountViewModel,
                            siteViewModel = siteViewModel,
                            postEditViewModel = postEditViewModel
                        )
                    }
                    composable(
                        route = "community/{id}",
                        arguments = listOf(
                            navArgument("id") {
                                type = NavType.IntType
                            }
                        )
                    ) {
                        LaunchedEffect(Unit) {
                            val communityId = it.arguments?.getInt("id")!!
                            val idOrName = Either.Left(communityId)

                            communityViewModel.fetchCommunity(
                                idOrName = idOrName,
                                auth = account?.jwt
                            )

                            communityViewModel.fetchPosts(
                                communityIdOrName = idOrName,
                                account = account,
                                clear = true,
                                ctx = ctx
                            )
                        }

                        CommunityActivity(
                            navController = navController,
                            communityViewModel = communityViewModel,
                            accountViewModel = accountViewModel,
                            homeViewModel = homeViewModel,
                            postEditViewModel = postEditViewModel,
                            communityListViewModel = communityListViewModel
                        )
                    }
                    // Only necessary for community deeplinks
                    composable(
                        route = "c/{name}",
                        deepLinks = DEFAULT_LEMMY_INSTANCES.map { instance ->
                            navDeepLink { uriPattern = "$instance/c/{name}" }
                        },
                        arguments = listOf(
                            navArgument("name") {
                                type = NavType.StringType
                            }
                        )
                    ) {
                        LaunchedEffect(Unit) {
                            val name = it.arguments?.getString("name")!!
                            val idOrName = Either.Right(name)

                            communityViewModel.fetchCommunity(
                                idOrName = idOrName,
                                auth = account?.jwt
                            )

                            communityViewModel.fetchPosts(
                                communityIdOrName = idOrName,
                                account = account,
                                clear = true,
                                ctx = ctx
                            )
                        }

                        CommunityActivity(
                            navController = navController,
                            communityViewModel = communityViewModel,
                            accountViewModel = accountViewModel,
                            homeViewModel = homeViewModel,
                            postEditViewModel = postEditViewModel,
                            communityListViewModel = communityListViewModel
                        )
                    }
                    composable(
                        route = "profile/{id}?saved={saved}",
                        arguments = listOf(
                            navArgument("id") {
                                type = NavType.IntType
                            },
                            navArgument("saved") {
                                defaultValue = false
                                type = NavType.BoolType
                            }
                        )
                    ) {
                        val savedMode = it.arguments?.getBoolean("saved")!!

                        LaunchedEffect(Unit) {
                            val personId = it.arguments?.getInt("id")!!
                            val idOrName = Either.Left(personId)

                            personProfileViewModel.fetchPersonDetails(
                                idOrName = idOrName,
                                account = account,
                                clear = true,
                                ctx = ctx,
                                changeSavedOnly = savedMode
                            )
                        }

                        PersonProfileActivity(
                            savedMode = savedMode,
                            navController = navController,
                            personProfileViewModel = personProfileViewModel,
                            accountViewModel = accountViewModel,
                            homeViewModel = homeViewModel,
                            commentEditViewModel = commentEditViewModel,
                            commentReplyViewModel = commentReplyViewModel,
                            postEditViewModel = postEditViewModel
                        )
                    }
                    // Necessary for deep links
                    composable(
                        route = "u/{name}",
                        deepLinks = DEFAULT_LEMMY_INSTANCES.map { instance ->
                            navDeepLink { uriPattern = "$instance/u/{name}" }
                        },
                        arguments = listOf(
                            navArgument("name") {
                                type = NavType.StringType
                            }
                        )
                    ) {
                        LaunchedEffect(Unit) {
                            val name = it.arguments?.getString("name")!!
                            val idOrName = Either.Right(name)

                            personProfileViewModel.fetchPersonDetails(
                                idOrName = idOrName,
                                account = account,
                                clear = true,
                                ctx = ctx
                            )
                        }

                        PersonProfileActivity(
                            savedMode = false,
                            navController = navController,
                            personProfileViewModel = personProfileViewModel,
                            accountViewModel = accountViewModel,
                            homeViewModel = homeViewModel,
                            commentEditViewModel = commentEditViewModel,
                            commentReplyViewModel = commentReplyViewModel,
                            postEditViewModel = postEditViewModel
                        )
                    }
                    composable(
                        route = "communityList?select={select}",
                        arguments = listOf(
                            navArgument("select") {
                                defaultValue = false
                                type = NavType.BoolType
                            }
                        )
                    ) {
                        // Whenever navigating here, reset the list with your followed communities
                        communityListViewModel.setCommunityListFromFollowed(siteViewModel)

                        CommunityListActivity(
                            navController = navController,
                            accountViewModel = accountViewModel,
                            communityListViewModel = communityListViewModel,
                            selectMode = it.arguments?.getBoolean("select")!!
                        )
                    }
                    composable(
                        route = "createPost",
                        deepLinks = listOf(
                            navDeepLink { mimeType = "text/plain" },
                            navDeepLink { mimeType = "image/*" }
                        )
                    ) {
                        val activity = ctx.findActivity()
                        val text = activity?.intent?.getStringExtra(Intent.EXTRA_TEXT) ?: ""
                        val image =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                activity?.intent?.getParcelableExtra(
                                    Intent.EXTRA_STREAM,
                                    Uri::class.java
                                )
                            } else {
                                @Suppress("DEPRECATION")
                                activity?.intent?.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                            }
                        // url and body will be empty everytime except when there is EXTRA TEXT in the intent
                        var url = ""
                        var body = ""
                        if (Patterns.WEB_URL.matcher(text).matches()) {
                            url = text
                        } else {
                            body = text
                        }

                        CreatePostActivity(
                            navController = navController,
                            accountViewModel = accountViewModel,
                            createPostViewModel = createPostViewModel,
                            communityListViewModel = communityListViewModel,
                            _url = url,
                            _body = body,
                            _image = image
                        )
                        activity?.intent?.replaceExtras(Bundle())
                    }
                    composable(
                        route = "inbox",
                        deepLinks = DEFAULT_LEMMY_INSTANCES.map { instance ->
                            navDeepLink { uriPattern = "$instance/inbox" }
                        }
                    ) {
                        if (account != null) {
                            LaunchedEffect(Unit) {
                                inboxViewModel.fetchReplies(
                                    account = account,
                                    clear = true,
                                    ctx = ctx
                                )
                                inboxViewModel.fetchPersonMentions(
                                    account = account,
                                    clear = true,
                                    ctx = ctx
                                )
                                inboxViewModel.fetchPrivateMessages(
                                    account = account,
                                    clear = true,
                                    ctx = ctx
                                )
                            }
                        }

                        InboxActivity(
                            navController = navController,
                            inboxViewModel = inboxViewModel,
                            accountViewModel = accountViewModel,
                            homeViewModel = homeViewModel,
                            commentEditViewModel = commentEditViewModel,
                            commentReplyViewModel = commentReplyViewModel
                        )
                    }
                    composable(
                        route = "post/{id}",
                        deepLinks = DEFAULT_LEMMY_INSTANCES.map { instance ->
                            navDeepLink { uriPattern = "$instance/post/{id}" }
                        },
                        arguments = listOf(
                            navArgument("id") {
                                type = NavType.IntType
                            }
                        )
                    ) {
                        LaunchedEffect(Unit) {
                            val postId = it.arguments?.getInt("id")!!
                            postViewModel.fetchPost(
                                id = postId,
                                account = account,
                                clear = true,
                                ctx = ctx
                            )
                        }
                        PostActivity(
                            postViewModel = postViewModel,
                            accountViewModel = accountViewModel,
                            commentEditViewModel = commentEditViewModel,
                            commentReplyViewModel = commentReplyViewModel,
                            postEditViewModel = postEditViewModel,
                            navController = navController
                        )
                    }
                    composable(
                        route = "commentReply"
                    ) {
                        CommentReplyActivity(
                            commentReplyViewModel = commentReplyViewModel,
                            postViewModel = postViewModel,
                            accountViewModel = accountViewModel,
                            personProfileViewModel = personProfileViewModel,
                            inboxViewModel = inboxViewModel,
                            navController = navController
                        )
                    }
                    composable(
                        route = "siteSidebar"
                    ) {
                        SiteSidebarActivity(
                            siteViewModel = siteViewModel,
                            navController = navController
                        )
                    }
                    composable(
                        route = "communitySidebar"
                    ) {
                        CommunitySidebarActivity(
                            communityViewModel = communityViewModel,
                            navController = navController
                        )
                    }
                    composable(
                        route = "commentEdit"
                    ) {
                        CommentEditActivity(
                            commentEditViewModel = commentEditViewModel,
                            accountViewModel = accountViewModel,
                            navController = navController,
                            personProfileViewModel = personProfileViewModel,
                            postViewModel = postViewModel,
                            inboxViewModel = inboxViewModel
                        )
                    }
                    composable(
                        route = "postEdit"
                    ) {
                        PostEditActivity(
                            postEditViewModel = postEditViewModel,
                            communityViewModel = communityViewModel,
                            accountViewModel = accountViewModel,
                            navController = navController,
                            personProfileViewModel = personProfileViewModel,
                            postViewModel = postViewModel,
                            homeViewModel = homeViewModel
                        )
                    }
                    composable(
                        route = "privateMessageReply"
                    ) {
                        PrivateMessageReplyActivity(
                            inboxViewModel = inboxViewModel,
                            accountViewModel = accountViewModel,
                            navController = navController
                        )
                    }
                    composable(
                        route = "commentReport/{id}",
                        arguments = listOf(
                            navArgument("id") {
                                type = NavType.IntType
                            }
                        )
                    ) {
                        createReportViewModel.setCommentId(it.arguments?.getInt("id")!!)
                        CreateCommentReportActivity(
                            createReportViewModel = createReportViewModel,
                            accountViewModel = accountViewModel,
                            navController = navController
                        )
                    }
                    composable(
                        route = "postReport/{id}",
                        arguments = listOf(
                            navArgument("id") {
                                type = NavType.IntType
                            }
                        )
                    ) {
                        createReportViewModel.setPostId(it.arguments?.getInt("id")!!)
                        CreatePostReportActivity(
                            createReportViewModel = createReportViewModel,
                            accountViewModel = accountViewModel,
                            navController = navController
                        )
                    }
                    composable(
                        route = "settings",
                        deepLinks = DEFAULT_LEMMY_INSTANCES.map { instance ->
                            navDeepLink { uriPattern = "$instance/settings" }
                        }
                    ) {
                        SettingsActivity(
                            navController = navController,
                            accountViewModel = accountViewModel,
                            siteViewModel = siteViewModel,
                            settingsViewModel = settingsViewModel
                        )
                    }
                }
            }
        }
    }
}

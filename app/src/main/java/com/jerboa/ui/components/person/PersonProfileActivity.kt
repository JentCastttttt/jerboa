package com.jerboa.ui.components.person

import android.content.Context
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import arrow.core.Either
import com.jerboa.JerboaAppState
import com.jerboa.R
import com.jerboa.api.ApiState
import com.jerboa.commentsToFlatNodes
import com.jerboa.datatypes.BanFromCommunityData
import com.jerboa.datatypes.VoteDisplayMode
import com.jerboa.datatypes.getDisplayName
import com.jerboa.datatypes.getLocalizedStringForUserTab
import com.jerboa.db.entity.Account
import com.jerboa.db.entity.isAnon
import com.jerboa.feat.BlurNSFW
import com.jerboa.feat.PostActionBarMode
import com.jerboa.feat.SwipeToActionPreset
import com.jerboa.feat.VoteType
import com.jerboa.feat.canMod
import com.jerboa.feat.doIfReadyElseDisplayInfo
import com.jerboa.feat.newVote
import com.jerboa.isScrolledToEnd
import com.jerboa.model.AccountViewModel
import com.jerboa.model.AppSettingsViewModel
import com.jerboa.model.PersonProfileViewModel
import com.jerboa.model.ReplyItem
import com.jerboa.model.SiteViewModel
import com.jerboa.scrollToTop
import com.jerboa.ui.components.ban.BanFromCommunityReturn
import com.jerboa.ui.components.ban.BanPersonReturn
import com.jerboa.ui.components.comment.CommentNodes
import com.jerboa.ui.components.comment.edit.CommentEditReturn
import com.jerboa.ui.components.comment.reply.CommentReplyReturn
import com.jerboa.ui.components.common.ApiEmptyText
import com.jerboa.ui.components.common.ApiErrorText
import com.jerboa.ui.components.common.JerboaPullRefreshIndicator
import com.jerboa.ui.components.common.JerboaSnackbarHost
import com.jerboa.ui.components.common.LoadingBar
import com.jerboa.ui.components.common.apiErrorToast
import com.jerboa.ui.components.common.getCurrentAccount
import com.jerboa.ui.components.common.getPostViewMode
import com.jerboa.ui.components.common.isLoading
import com.jerboa.ui.components.common.isRefreshing
import com.jerboa.ui.components.common.simpleVerticalScrollbar
import com.jerboa.ui.components.community.CommunityLink
import com.jerboa.ui.components.post.PostListings
import com.jerboa.ui.components.post.PostViewReturn
import com.jerboa.ui.components.post.edit.PostEditReturn
import com.jerboa.ui.components.remove.comment.CommentRemoveReturn
import com.jerboa.ui.components.remove.post.PostRemoveReturn
import com.jerboa.ui.theme.MEDIUM_PADDING
import it.vercruysse.lemmyapi.v0x19.datatypes.BlockPerson
import it.vercruysse.lemmyapi.v0x19.datatypes.CommentId
import it.vercruysse.lemmyapi.v0x19.datatypes.CommentView
import it.vercruysse.lemmyapi.v0x19.datatypes.CreateCommentLike
import it.vercruysse.lemmyapi.v0x19.datatypes.CreatePostLike
import it.vercruysse.lemmyapi.v0x19.datatypes.DeleteComment
import it.vercruysse.lemmyapi.v0x19.datatypes.DeletePost
import it.vercruysse.lemmyapi.v0x19.datatypes.DistinguishComment
import it.vercruysse.lemmyapi.v0x19.datatypes.FeaturePost
import it.vercruysse.lemmyapi.v0x19.datatypes.GetPersonDetails
import it.vercruysse.lemmyapi.v0x19.datatypes.LockPost
import it.vercruysse.lemmyapi.v0x19.datatypes.MarkPostAsRead
import it.vercruysse.lemmyapi.v0x19.datatypes.PersonId
import it.vercruysse.lemmyapi.v0x19.datatypes.PersonView
import it.vercruysse.lemmyapi.v0x19.datatypes.PostView
import it.vercruysse.lemmyapi.v0x19.datatypes.SaveComment
import it.vercruysse.lemmyapi.v0x19.datatypes.SavePost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonProfileActivity(
    personArg: Either<PersonId, String>,
    savedMode: Boolean,
    appState: JerboaAppState,
    accountViewModel: AccountViewModel,
    siteViewModel: SiteViewModel,
    appSettingsViewModel: AppSettingsViewModel,
    showVotingArrowsInListView: Boolean,
    useCustomTabs: Boolean,
    usePrivateTabs: Boolean,
    blurNSFW: BlurNSFW,
    showPostLinkPreviews: Boolean,
    drawerState: DrawerState,
    markAsReadOnScroll: Boolean,
    postActionBarMode: PostActionBarMode,
    onBack: (() -> Unit)? = null,
    swipeToActionPreset: SwipeToActionPreset,
) {
    Log.d("jerboa", "got to person activity")

    val scope = rememberCoroutineScope()
    val postListState = rememberLazyListState()
    val ctx = LocalContext.current
    val account = getCurrentAccount(accountViewModel)
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    val personProfileViewModel: PersonProfileViewModel =
        viewModel(factory = PersonProfileViewModel.Companion.Factory(personArg, savedMode))

    appState.ConsumeReturn<PostView>(PostEditReturn.POST_VIEW, personProfileViewModel::updatePost)
    appState.ConsumeReturn<PostView>(PostRemoveReturn.POST_VIEW, personProfileViewModel::updatePost)
    appState.ConsumeReturn<CommentView>(CommentEditReturn.COMMENT_VIEW, personProfileViewModel::updateComment)
    appState.ConsumeReturn<CommentView>(CommentRemoveReturn.COMMENT_VIEW, personProfileViewModel::updateComment)
    appState.ConsumeReturn<PersonView>(BanPersonReturn.PERSON_VIEW, personProfileViewModel::updateBanned)
    appState.ConsumeReturn<BanFromCommunityData>(BanFromCommunityReturn.BAN_DATA_VIEW, personProfileViewModel::updateBannedFromCommunity)

    appState.ConsumeReturn<CommentView>(CommentReplyReturn.COMMENT_VIEW) { cv ->
        when (val res = personProfileViewModel.personDetailsRes) {
            is ApiState.Success -> {
                if (account.id == res.data.person_view.person.id) {
                    personProfileViewModel.insertComment(cv)
                }
            }
            else -> {}
        }
    }

    fun openDrawer() {
        scope.launch {
            drawerState.open()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { JerboaSnackbarHost(snackbarHostState) },
        topBar = {
            when (val profileRes = personProfileViewModel.personDetailsRes) {
                is ApiState.Failure -> apiErrorToast(ctx, profileRes.msg)
                ApiState.Loading, ApiState.Refreshing -> {
                    // Prevents tabs from jumping around during loading/refreshing
                    PersonProfileHeader(
                        personName = ctx.getString(R.string.loading),
                        myProfile = false,
                        banned = false,
                        canBan = false,
                        onClickSortType = {},
                        onBlockPersonClick = {},
                        onReportPersonClick = {},
                        onMessagePersonClick = {},
                        onBanPersonClick = {},
                        selectedSortType = personProfileViewModel.sortType,
                        openDrawer = ::openDrawer,
                        scrollBehavior = scrollBehavior,
                        onBack = onBack,
                        isLoggedIn = { false },
                        matrixId = null,
                    )
                }
                is ApiState.Holder -> {
                    val person = profileRes.data.person_view.person
                    val canBan = canMod(
                        creatorId = person.id,
                        admins = siteViewModel.admins(),
                        moderators = null,
                        myId = account.id,
                    )

                    PersonProfileHeader(
                        scrollBehavior = scrollBehavior,
                        personName =
                            if (savedMode) {
                                ctx.getString(R.string.bookmarks_activity_saved)
                            } else {
                                person.name
                            },
                        myProfile = account.id == person.id,
                        selectedSortType = personProfileViewModel.sortType,
                        banned = person.banned,
                        canBan = canBan,
                        onClickSortType = { sortType ->
                            scrollToTop(scope, postListState)
                            personProfileViewModel.resetPage()
                            personProfileViewModel.updateSortType(sortType)
                            personProfileViewModel.getPersonDetails(
                                GetPersonDetails(
                                    person_id = person.id,
                                    sort = personProfileViewModel.sortType,
                                    page = personProfileViewModel.page,
                                    saved_only = personProfileViewModel.savedOnly,
                                ),
                            )
                        },
                        onBlockPersonClick = {
                            account.doIfReadyElseDisplayInfo(
                                appState,
                                ctx,
                                snackbarHostState,
                                scope,
                                siteViewModel,
                                accountViewModel,
                            ) {
                                personProfileViewModel.blockPerson(
                                    BlockPerson(
                                        person_id = person.id,
                                        block = true,
                                    ),
                                    ctx,
                                )
                            }
                        },
                        onReportPersonClick = {
                            val firstComment = profileRes.data.comments.firstOrNull()
                            val firstPost = profileRes.data.posts.firstOrNull()
                            if (firstComment !== null) {
                                appState.toCommentReport(id = firstComment.comment.id)
                            } else if (firstPost !== null) {
                                appState.toPostReport(id = firstPost.post.id)
                            }
                        },
                        onMessagePersonClick = {
                            appState.toCreatePrivateMessage(
                                person.id,
                                person.getDisplayName(),
                            )
                        },
                        onBanPersonClick = {
                            appState.toBanPerson(person)
                        },
                        openDrawer = ::openDrawer,
                        onBack = onBack,
                        isLoggedIn = { !account.isAnon() },
                        matrixId = person.matrix_user_id,
                    )
                }
                else -> {}
            }
        },
        content = {
            UserTabs(
                savedMode = savedMode,
                padding = it,
                appState = appState,
                personProfileViewModel = personProfileViewModel,
                siteViewModel = siteViewModel,
                ctx = ctx,
                account = account,
                scope = scope,
                postListState = postListState,
                appSettingsViewModel = appSettingsViewModel,
                showVotingArrowsInListView = showVotingArrowsInListView,
                enableDownVotes = siteViewModel.enableDownvotes(),
                showAvatar = siteViewModel.showAvatar(),
                useCustomTabs = useCustomTabs,
                usePrivateTabs = usePrivateTabs,
                blurNSFW = blurNSFW,
                showPostLinkPreviews = showPostLinkPreviews,
                markAsReadOnScroll = markAsReadOnScroll,
                snackbarHostState = snackbarHostState,
                voteDisplayMode = siteViewModel.voteDisplayMode(),
                postActionBarMode = postActionBarMode,
                swipeToActionPreset = swipeToActionPreset,
            )
        },
    )
}

enum class UserTab {
    About,
    Posts,
    Comments,
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun UserTabs(
    appState: JerboaAppState,
    savedMode: Boolean,
    personProfileViewModel: PersonProfileViewModel,
    siteViewModel: SiteViewModel,
    ctx: Context,
    account: Account,
    scope: CoroutineScope,
    postListState: LazyListState,
    padding: PaddingValues,
    appSettingsViewModel: AppSettingsViewModel,
    showVotingArrowsInListView: Boolean,
    enableDownVotes: Boolean,
    showAvatar: Boolean,
    useCustomTabs: Boolean,
    usePrivateTabs: Boolean,
    blurNSFW: BlurNSFW,
    showPostLinkPreviews: Boolean,
    markAsReadOnScroll: Boolean,
    snackbarHostState: SnackbarHostState,
    voteDisplayMode: VoteDisplayMode,
    postActionBarMode: PostActionBarMode,
    swipeToActionPreset: SwipeToActionPreset,
) {
    val tabTitles =
        if (savedMode) {
            listOf(
                getLocalizedStringForUserTab(ctx, UserTab.Posts),
                getLocalizedStringForUserTab(ctx, UserTab.Comments),
            )
        } else {
            UserTab.entries.map { getLocalizedStringForUserTab(ctx, it) }
        }
    val pagerState = rememberPagerState { tabTitles.size }

    val loading = personProfileViewModel.personDetailsRes.isLoading()

    appState.ConsumeReturn<PostView>(PostViewReturn.POST_VIEW, personProfileViewModel::updatePost)

    val pullRefreshState =
        rememberPullRefreshState(
            refreshing = personProfileViewModel.personDetailsRes.isRefreshing(),
            onRefresh = {
                when (val profileRes = personProfileViewModel.personDetailsRes) {
                    is ApiState.Success -> {
                        personProfileViewModel.resetPage()
                        personProfileViewModel.getPersonDetails(
                            GetPersonDetails(
                                person_id = profileRes.data.person_view.person.id,
                                sort = personProfileViewModel.sortType,
                                page = personProfileViewModel.page,
                                saved_only = personProfileViewModel.savedOnly,
                            ),
                            ApiState.Refreshing,
                        )
                    }
                    else -> {}
                }
            },
        )

    Column(
        modifier = Modifier.padding(padding),
    ) {
        TabRow(
            selectedTabIndex = pagerState.currentPage,
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = { Text(text = title) },
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxSize(),
        ) { tabIndex ->
            // Need an offset for the saved mode, which doesn't show about
            val tabI =
                if (!savedMode) {
                    tabIndex
                } else {
                    tabIndex + 1
                }
            when (tabI) {
                UserTab.About.ordinal -> {
                    when (val profileRes = personProfileViewModel.personDetailsRes) {
                        ApiState.Empty -> ApiEmptyText()
                        is ApiState.Failure -> ApiErrorText(profileRes.msg)
                        ApiState.Loading -> LoadingBar()
                        is ApiState.Success -> {
                            val listState = rememberLazyListState()

                            LazyColumn(
                                state = listState,
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .simpleVerticalScrollbar(listState),
                            ) {
                                item(contentType = "topSection") {
                                    PersonProfileTopSection(
                                        personView = profileRes.data.person_view,
                                        showAvatar = showAvatar,
                                        openImageViewer = appState::openImageViewer,
                                    )
                                }
                                val moderates = profileRes.data.moderates
                                if (moderates.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = stringResource(R.string.person_profile_activity_moderates),
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.padding(MEDIUM_PADDING),
                                        )
                                    }
                                }
                                items(
                                    moderates,
                                    key = { cmv -> cmv.community.id },
                                    contentType = { "communitylink" },
                                ) { cmv ->
                                    CommunityLink(
                                        community = cmv.community,
                                        modifier = Modifier.padding(MEDIUM_PADDING),
                                        onClick = { community ->
                                            appState.toCommunity(id = community.id)
                                        },
                                        showDefaultIcon = true,
                                        showAvatar = showAvatar,
                                        blurNSFW = blurNSFW,
                                    )
                                }
                            }
                        }
                        else -> {}
                    }
                }

                UserTab.Posts.ordinal -> {
                    Box(
                        modifier =
                            Modifier
                                .pullRefresh(pullRefreshState)
                                .fillMaxSize(),
                    ) {
                        JerboaPullRefreshIndicator(
                            personProfileViewModel.personDetailsRes.isRefreshing(),
                            pullRefreshState,
                            // zIndex needed bc some elements of a post get drawn above it.
                            Modifier
                                .align(Alignment.TopCenter)
                                .zIndex(100f),
                        )
                        if (loading) {
                            LoadingBar()
                        }

                        when (val profileRes = personProfileViewModel.personDetailsRes) {
                            ApiState.Empty -> ApiEmptyText()
                            is ApiState.Failure -> ApiErrorText(profileRes.msg)
                            is ApiState.Holder -> {
                                PostListings(
                                    posts = profileRes.data.posts.toList(),
                                    admins = siteViewModel.admins(),
                                    // No community moderators available here
                                    moderators = null,
                                    onUpvoteClick = { pv ->
                                        account.doIfReadyElseDisplayInfo(
                                            appState,
                                            ctx,
                                            snackbarHostState,
                                            scope,
                                            loginAsToast = true,
                                        ) {
                                            personProfileViewModel.likePost(
                                                CreatePostLike(
                                                    post_id = pv.post.id,
                                                    score =
                                                        newVote(
                                                            pv.my_vote,
                                                            VoteType.Upvote,
                                                        ).toLong(),
                                                ),
                                            )
                                        }
                                    },
                                    onDownvoteClick = { pv ->
                                        account.doIfReadyElseDisplayInfo(
                                            appState,
                                            ctx,
                                            snackbarHostState,
                                            scope,
                                            loginAsToast = true,
                                        ) {
                                            personProfileViewModel.likePost(
                                                CreatePostLike(
                                                    post_id = pv.post.id,
                                                    score = newVote(
                                                        pv.my_vote,
                                                        VoteType.Downvote,
                                                    ).toLong(),
                                                ),
                                            )
                                        }
                                    },
                                    onPostClick = { pv ->
                                        appState.toPost(id = pv.post.id)
                                    },
                                    onSaveClick = { pv ->
                                        account.doIfReadyElseDisplayInfo(
                                            appState,
                                            ctx,
                                            snackbarHostState,
                                            scope,
                                            loginAsToast = true,
                                        ) {
                                            personProfileViewModel.savePost(
                                                SavePost(
                                                    post_id = pv.post.id,
                                                    save = !pv.saved,
                                                ),
                                            )
                                        }
                                    },
                                    onReplyClick = { pv ->
                                        appState.toCommentReply(
                                            replyItem = ReplyItem.PostItem(pv),
                                        )
                                    },
                                    onEditPostClick = { pv ->
                                        appState.toPostEdit(
                                            postView = pv,
                                        )
                                    },
                                    onDeletePostClick = { pv ->
                                        account.doIfReadyElseDisplayInfo(
                                            appState,
                                            ctx,
                                            snackbarHostState,
                                            scope,
                                            loginAsToast = true,
                                        ) {
                                            personProfileViewModel.deletePost(
                                                DeletePost(
                                                    post_id = pv.post.id,
                                                    deleted = !pv.post.deleted,
                                                ),
                                            )
                                        }
                                    },
                                    onReportClick = { pv ->
                                        appState.toPostReport(id = pv.post.id)
                                    },
                                    onRemoveClick = { pv ->
                                        appState.toPostRemove(post = pv.post)
                                    },
                                    onBanPersonClick = { p ->
                                        appState.toBanPerson(p)
                                    },
                                    onBanFromCommunityClick = { d ->
                                        appState.toBanFromCommunity(banData = d)
                                    },
                                    onLockPostClick = { pv ->
                                        account.doIfReadyElseDisplayInfo(
                                            appState,
                                            ctx,
                                            snackbarHostState,
                                            scope,
                                            loginAsToast = true,
                                        ) {
                                            personProfileViewModel.lockPost(
                                                LockPost(
                                                    post_id = pv.post.id,
                                                    locked = !pv.post.locked,
                                                ),
                                            )
                                        }
                                    },
                                    onFeaturePostClick = { data ->
                                        account.doIfReadyElseDisplayInfo(
                                            appState,
                                            ctx,
                                            snackbarHostState,
                                            scope,
                                            loginAsToast = true,
                                        ) {
                                            personProfileViewModel.featurePost(
                                                FeaturePost(
                                                    post_id = data.post.id,
                                                    featured = !data.featured,
                                                    feature_type = data.type,
                                                ),
                                            )
                                        }
                                    },
                                    onViewPostVotesClick = appState::toPostLikes,
                                    onCommunityClick = { community ->
                                        appState.toCommunity(id = community.id)
                                    },
                                    onPersonClick = appState::toProfile,
                                    loadMorePosts = {
                                        personProfileViewModel.appendData(
                                            profileRes.data.person_view.person.id,
                                        )
                                    },
                                    account = account,
                                    listState = postListState,
                                    postViewMode = getPostViewMode(appSettingsViewModel),
                                    showVotingArrowsInListView = showVotingArrowsInListView,
                                    enableDownVotes = enableDownVotes,
                                    showAvatar = showAvatar,
                                    useCustomTabs = useCustomTabs,
                                    usePrivateTabs = usePrivateTabs,
                                    blurNSFW = blurNSFW,
                                    showPostLinkPreviews = showPostLinkPreviews,
                                    appState = appState,
                                    markAsReadOnScroll = markAsReadOnScroll,
                                    onMarkAsRead = {
                                        if (!account.isAnon() && !it.read) {
                                            personProfileViewModel.markPostAsRead(
                                                MarkPostAsRead(
                                                    post_ids = listOf(it.post.id),
                                                    read = true,
                                                ),
                                                it,
                                                appState,
                                            )
                                        }
                                    },
                                    showIfRead = false,
                                    voteDisplayMode = voteDisplayMode,
                                    postActionBarMode = postActionBarMode,
                                    showPostAppendRetry = personProfileViewModel.personDetailsRes is ApiState.AppendingFailure,
                                    swipeToActionPreset = swipeToActionPreset,
                                )
                            }
                            else -> {}
                        }
                    }
                }

                UserTab.Comments.ordinal -> {
                    when (val profileRes = personProfileViewModel.personDetailsRes) {
                        ApiState.Empty -> ApiEmptyText()
                        is ApiState.Failure -> ApiErrorText(profileRes.msg)
                        ApiState.Loading -> LoadingBar()
                        ApiState.Refreshing -> LoadingBar()
                        is ApiState.Holder -> {
                            val nodes = commentsToFlatNodes(profileRes.data.comments)

                            val listState = rememberLazyListState()

                            // observer when reached end of list
                            val endOfListReached by remember {
                                derivedStateOf {
                                    listState.isScrolledToEnd()
                                }
                            }

                            // Holds the un-expanded comment ids
                            val unExpandedComments = remember { mutableStateListOf<Long>() }
                            val commentsWithToggledActionBar = remember { mutableStateListOf<Long>() }

                            val toggleExpanded = remember {
                                { commentId: CommentId ->
                                    if (unExpandedComments.contains(commentId)) {
                                        unExpandedComments.remove(commentId)
                                    } else {
                                        unExpandedComments.add(commentId)
                                    }
                                }
                            }

                            val toggleActionBar = remember {
                                { commentId: CommentId ->
                                    if (commentsWithToggledActionBar.contains(commentId)) {
                                        commentsWithToggledActionBar.remove(commentId)
                                    } else {
                                        commentsWithToggledActionBar.add(commentId)
                                    }
                                }
                            }

                            val showActionBarByDefault = true

                            // act when end of list reached
                            if (endOfListReached) {
                                LaunchedEffect(Unit) {
                                    personProfileViewModel.appendData(
                                        profileRes.data.person_view.person.id,
                                    )
                                }
                            }

                            Box(
                                modifier =
                                    Modifier
                                        .pullRefresh(pullRefreshState)
                                        .fillMaxSize(),
                            ) {
                                JerboaPullRefreshIndicator(
                                    personProfileViewModel.personDetailsRes.isRefreshing(),
                                    pullRefreshState,
                                    // zIndex needed bc some elements of a post get drawn above it.
                                    Modifier
                                        .align(Alignment.TopCenter)
                                        .zIndex(100f),
                                )
                                if (loading) {
                                    LoadingBar()
                                }
                                CommentNodes(
                                    nodes = nodes,
                                    admins = siteViewModel.admins(),
                                    // No community moderators available here
                                    moderators = null,
                                    increaseLazyListIndexTracker = {},
                                    addToParentIndexes = {},
                                    isFlat = true,
                                    isExpanded = { commentId -> !unExpandedComments.contains(commentId) },
                                    listState = listState,
                                    toggleExpanded = { commentId -> toggleExpanded(commentId) },
                                    toggleActionBar = { commentId -> toggleActionBar(commentId) },
                                    onMarkAsReadClick = {},
                                    onCommentClick = { cv ->
                                        appState.toComment(id = cv.comment.id)
                                    },
                                    onUpvoteClick = { cv ->
                                        account.doIfReadyElseDisplayInfo(
                                            appState,
                                            ctx,
                                            snackbarHostState,
                                            scope,
                                            loginAsToast = true,
                                        ) {
                                            personProfileViewModel.likeComment(
                                                CreateCommentLike(
                                                    comment_id = cv.comment.id,
                                                    score = newVote(cv.my_vote, VoteType.Upvote).toLong(),
                                                ),
                                            )
                                        }
                                    },
                                    onDownvoteClick = { cv ->
                                        account.doIfReadyElseDisplayInfo(
                                            appState,
                                            ctx,
                                            snackbarHostState,
                                            scope,
                                            loginAsToast = true,
                                        ) {
                                            personProfileViewModel.likeComment(
                                                CreateCommentLike(
                                                    comment_id = cv.comment.id,
                                                    score = newVote(cv.my_vote, VoteType.Downvote).toLong(),
                                                ),
                                            )
                                        }
                                    },
                                    onReplyClick = { cv ->
                                        appState.toCommentReply(
                                            replyItem = ReplyItem.CommentItem(cv),
                                        )
                                    },
                                    onSaveClick = { cv ->
                                        account.doIfReadyElseDisplayInfo(
                                            appState,
                                            ctx,
                                            snackbarHostState,
                                            scope,
                                            loginAsToast = true,
                                        ) {
                                            personProfileViewModel.saveComment(
                                                SaveComment(
                                                    comment_id = cv.comment.id,
                                                    save = !cv.saved,
                                                ),
                                            )
                                        }
                                    },
                                    onPersonClick = appState::toProfile,
                                    onViewVotesClick = appState::toCommentLikes,
                                    onHeaderClick = {},
                                    onHeaderLongClick = { commentView -> toggleActionBar(commentView.comment.id) },
                                    onCommunityClick = { community ->
                                        appState.toCommunity(id = community.id)
                                    },
                                    onPostClick = { postId ->
                                        appState.toPost(id = postId)
                                    },
                                    onEditCommentClick = { cv ->
                                        appState.toCommentEdit(
                                            commentView = cv,
                                        )
                                    },
                                    onDeleteCommentClick = { cv ->
                                        account.doIfReadyElseDisplayInfo(
                                            appState,
                                            ctx,
                                            snackbarHostState,
                                            scope,
                                            loginAsToast = true,
                                        ) {
                                            personProfileViewModel.deleteComment(
                                                DeleteComment(
                                                    comment_id = cv.comment.id,
                                                    deleted = !cv.comment.deleted,
                                                ),
                                            )
                                        }
                                    },
                                    onDistinguishClick = { cv ->
                                        account.doIfReadyElseDisplayInfo(
                                            appState,
                                            ctx,
                                            snackbarHostState,
                                            scope,
                                            loginAsToast = true,
                                        ) {
                                            personProfileViewModel.distinguishComment(
                                                DistinguishComment(
                                                    comment_id = cv.comment.id,
                                                    distinguished = !cv.comment.distinguished,
                                                ),
                                            )
                                        }
                                    },
                                    onReportClick = { cv ->
                                        appState.toCommentReport(id = cv.comment.id)
                                    },
                                    onRemoveClick = { cv ->
                                        appState.toCommentRemove(comment = cv.comment)
                                    },
                                    onBanPersonClick = { p ->
                                        appState.toBanPerson(p)
                                    },
                                    onBanFromCommunityClick = { d ->
                                        appState.toBanFromCommunity(banData = d)
                                    },
                                    onCommentLinkClick = { cv ->
                                        appState.toComment(id = cv.comment.id)
                                    },
                                    onFetchChildrenClick = {},
                                    onBlockCreatorClick = { person ->
                                        account.doIfReadyElseDisplayInfo(
                                            appState,
                                            ctx,
                                            snackbarHostState,
                                            scope,
                                            loginAsToast = true,
                                        ) {
                                            personProfileViewModel.blockPerson(
                                                BlockPerson(
                                                    person_id = person.id,
                                                    block = true,
                                                ),
                                                ctx,
                                            )
                                        }
                                    },
                                    showPostAndCommunityContext = true,
                                    showCollapsedCommentContent = true,
                                    isCollapsedByParent = false,
                                    showActionBar = { commentId ->
                                        showActionBarByDefault xor commentsWithToggledActionBar.contains(commentId)
                                    },
                                    account = account,
                                    enableDownVotes = enableDownVotes,
                                    showAvatar = showAvatar,
                                    blurNSFW = blurNSFW,
                                    voteDisplayMode = voteDisplayMode,
                                    swipeToActionPreset = swipeToActionPreset,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

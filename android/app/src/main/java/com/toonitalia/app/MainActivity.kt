package com.toonitalia.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContent {
                ToonItaliaTheme {
                    MainScreen()
                }
            }
        } catch (t: Throwable) {
            val msg = "${t.javaClass.simpleName}: ${t.message}\n\n${t.stackTraceToString()}"
            try {
                val f = java.io.File(filesDir, "crash_log.txt")
                f.writeText("=== INIT CRASH ===\n$msg")
                val ext = getExternalFilesDir(null)
                if (ext != null) java.io.File(ext, "crash_log.txt").writeText("=== INIT CRASH ===\n$msg")
            } catch (_: Exception) {}
            setContent {
                ToonItaliaTheme {
                    ErrorScreen(msg)
                }
            }
        }
    }
}

@Composable
fun ToonItaliaTheme(content: @Composable () -> Unit) {
    val darkColorScheme = darkColorScheme(
        primary = Color(0xFF6C63FF),
        secondary = Color(0xFF03DAC5),
        background = Color(0xFF0F0F23),
        surface = Color(0xFF1A1A2E),
        onPrimary = Color.White,
        onSecondary = Color.Black,
        onBackground = Color.White,
        onSurface = Color.White,
    )
    MaterialTheme(
        colorScheme = darkColorScheme,
        typography = Typography(
            headlineLarge = Typography().headlineLarge.copy(fontWeight = FontWeight.Bold),
            titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.SemiBold),
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var currentScreen by remember { mutableStateOf("home") }
    var selectedContent by remember { mutableStateOf<ContentItem?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isTv = ToonItaliaApp.isTV(context)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ToonItalia", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text("v1.0", fontSize = 12.sp, color = Color.Gray)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E)
                ),
                actions = {
                    IconButton(onClick = { currentScreen = "crash" }) {
                        Icon(Icons.Default.BugReport, "Crash Logs")
                    }
                    IconButton(onClick = { currentScreen = "search" }) {
                        Icon(Icons.Default.Search, "Search")
                    }
                    IconButton(onClick = { currentScreen = "home" }) {
                        Icon(Icons.Default.Home, "Home")
                    }
                }
            )
        },
        bottomBar = {
            if (!isTv) {
                NavigationBar(containerColor = Color(0xFF1A1A2E)) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, "Home") },
                        label = { Text("Home") },
                        selected = currentScreen == "home",
                        onClick = { currentScreen = "home" }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Animation, "Anime") },
                        label = { Text("Anime") },
                        selected = currentScreen == "anime-ita",
                        onClick = { currentScreen = "anime-ita" }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Subtitles, "Sub-Ita") },
                        label = { Text("Sub-Ita") },
                        selected = currentScreen == "contatti",
                        onClick = { currentScreen = "contatti" }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Movie, "Film") },
                        label = { Text("Film") },
                        selected = currentScreen == "film-animazione",
                        onClick = { currentScreen = "film-animazione" }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Tv, "Serie TV") },
                        label = { Text("Serie TV") },
                        selected = currentScreen == "serie-tv",
                        onClick = { currentScreen = "serie-tv" }
                    )
                }
            }
        },
        containerColor = Color(0xFF0F0F23)
    ) { padding ->
        when {
            selectedContent != null -> {
                DetailScreen(
                    content = selectedContent!!,
                    onBack = { selectedContent = null },
                    onPlayEpisode = { episode ->
                        val intent = Intent(context, PlayerActivity::class.java).apply {
                            putExtra("video_url", episode.url)
                            putExtra("title", episode.title)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.padding(padding)
                )
            }
            currentScreen == "search" -> {
                SearchScreen(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onItemSelected = { selectedContent = it },
                    modifier = Modifier.padding(padding)
                )
            }
            currentScreen == "crash" -> {
                CrashScreen(modifier = Modifier.padding(padding))
            }
            currentScreen == "home" -> {
                HomeScreen(
                    onItemSelected = { selectedContent = it },
                    modifier = Modifier.padding(padding)
                )
            }
            else -> {
                CategoryScreen(
                    categorySlug = currentScreen,
                    onItemSelected = { selectedContent = it },
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
fun HomeScreen(
    onItemSelected: (ContentItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var sections by remember { mutableStateOf<List<CategorySection>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    sections = Scraper.scrapeHomepage()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            isLoading = false
        }
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF6C63FF))
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            items(sections) { section ->
                SectionHeader(title = section.title)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(section.items) { item ->
                        ContentCard(
                            item = item,
                            onClick = { onItemSelected(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryScreen(
    categorySlug: String,
    onItemSelected: (ContentItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var items by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(categorySlug) {
        isLoading = true
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    items = Scraper.scrapeContentList(categorySlug)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            isLoading = false
        }
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF6C63FF))
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items) { item ->
                ContentCard(
                    item = item,
                    onClick = { onItemSelected(item) }
                )
            }
        }
    }
}

@Composable
fun SearchScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    onItemSelected: (ContentItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var results by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                onQueryChange(it)
                if (it.length >= 2) {
                    isSearching = true
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            try {
                                results = Scraper.search(it)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        isSearching = false
                    }
                }
            },
            label = { Text("Cerca anime, film, serie...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(16.dp))

        if (isSearching) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(results) { item ->
                    ContentCard(
                        item = item,
                        onClick = { onItemSelected(item) }
                    )
                }
            }
        }
    }
}

@Composable
fun DetailScreen(
    content: ContentItem,
    onBack: () -> Unit,
    onPlayEpisode: (Episode) -> Unit,
    modifier: Modifier = Modifier
) {
    var detail by remember { mutableStateOf(content) }
    var isLoading by remember { mutableStateOf(content.episodes.isEmpty()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(content.url) {
        if (content.episodes.isEmpty()) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        detail = Scraper.scrapeDetail(content.url)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                isLoading = false
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Box {
                AsyncImage(
                    model = detail.image.ifEmpty { detail.thumbnail },
                    contentDescription = detail.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
                    contentScale = ContentScale.Crop
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xFF0F0F23))
                            )
                        )
                )
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        "Back",
                        tint = Color.White
                    )
                }
            }
        }

        item {
            Column(Modifier.padding(16.dp)) {
                Text(
                    detail.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (detail.originalTitle.isNotBlank()) {
                    Text(detail.originalTitle, color = Color.Gray, fontSize = 14.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (detail.year.isNotBlank()) InfoChip(detail.year)
                    if (detail.status.isNotBlank()) InfoChip(detail.status)
                    if (detail.country.isNotBlank()) InfoChip(detail.country)
                }
                if (detail.synopsis.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text("Trama", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text(detail.synopsis, color = Color(0xFFBBBBBB), lineHeight = 22.sp)
                }
            }
        }

        if (isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF6C63FF))
                }
            }
        }

        if (detail.episodes.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        "Episodi (${detail.episodes.size})",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            items(detail.episodes) { episode ->
                EpisodeItem(
                    episode = episode,
                    onClick = { onPlayEpisode(episode) }
                )
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        title,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
fun ContentCard(
    item: ContentItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
    ) {
        Column {
            AsyncImage(
                model = item.image,
                contentDescription = item.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                contentScale = ContentScale.Crop
            )
            Text(
                item.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
                color = Color.White,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
fun InfoChip(text: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF2A2A4A)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            fontSize = 12.sp,
            color = Color(0xFF03DAC5)
        )
    }
}

@Composable
fun EpisodeItem(
    episode: Episode,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF1A1A2E)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.PlayCircle,
                contentDescription = null,
                tint = Color(0xFF6C63FF),
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                episode.title,
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.Gray
            )
        }
    }
}

@Composable
fun CrashScreen(modifier: Modifier = Modifier) {
    var crashLog by remember { mutableStateOf<String>("") }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        scope.launch {
            withContext(Dispatchers.IO) {
                val file = context.getFileStreamPath("crash_log.txt")
                if (file.exists()) {
                    crashLog = file.readText()
                } else {
                    crashLog = "Nessun crash registrato."
                }
            }
            isLoading = false
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🐛 Crash Logs", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            IconButton(onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        context.getFileStreamPath("crash_log.txt").delete()
                    }
                    crashLog = "Log cancellato."
                }
            }) {
                Icon(Icons.Default.Delete, "Clear", tint = Color(0xFF03DAC5))
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A1A2E))
                    .padding(16.dp),
                contentAlignment = Alignment.TopStart
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = crashLog,
                    onValueChange = {},
                    modifier = Modifier.fillMaxSize(),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFFBBBBBB)
                    ),
                    readOnly = true,
                    singleLine = false,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                )
            }
        }
    }
}

@Composable
fun ErrorScreen(error: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F23))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.BugReport, null, tint = Color.Red, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("App Crashata", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Red)
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF1A1A2E))
                .padding(12.dp)
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = error,
                onValueChange = {},
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFFBBBBBB)
                ),
                readOnly = true
            )
        }
    }
}

package com.example.conteoinventarioplantacionarlessas.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.conteoinventarioplantacionarlessas.data.InventarioRepository

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController(),
    repository: InventarioRepository,
    sessionUid: String,
    accountEmail: String,
    onSignOut: () -> Unit
) {
    val mainViewModel: InventarioViewModel = viewModel(
        key = "inventario-$sessionUid",
        factory = InventarioViewModelFactory(repository)
    )

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                viewModel = mainViewModel,
                accountEmail = accountEmail,
                sessionUid = sessionUid,
                onSignOut = onSignOut,
                onNavigateToCenso = { tipo ->
                    navController.navigate("censo_lotes/$tipo")
                }
            )
        }

        composable("censo_lotes/{tipo}") { backStackEntry ->
            val tipo = backStackEntry.arguments?.getString("tipo") ?: "FRUTOS"
            CensoLotesScreen(
                tipo = tipo,
                viewModel = mainViewModel,
                onLineSelected = { censoId, lineaId ->
                    navController.navigate(if(tipo=="PRODUCCION") "censo_detalle/$tipo/$censoId/$lineaId" else "arboles/$tipo/$censoId/$lineaId")
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable("arboles/{tipo}/{censoId}/{lineaId}") { entry ->
            val tipo=entry.arguments?.getString("tipo") ?: "FRUTOS"
            val censo=entry.arguments?.getString("censoId")?.toIntOrNull() ?: 0
            val linea=entry.arguments?.getString("lineaId").orEmpty()
            TreeGridScreen(repository,tipo,censo,linea,onTree={pos->navController.navigate("censo_detalle/$tipo/$censo/$linea?pos=$pos")},onBack={navController.popBackStack()})
        }

        composable("censo_detalle/{tipo}/{censoId}/{lineaId}?pos={pos}",arguments=listOf(navArgument("pos") {type=NavType.IntType;defaultValue=-1})) { backStackEntry ->
            val tipo = backStackEntry.arguments?.getString("tipo") ?: "FRUTOS"
            val censoId = backStackEntry.arguments?.getString("censoId")?.toIntOrNull() ?: 0
            val lineaId = backStackEntry.arguments?.getString("lineaId") ?: ""

            val initialPosition=backStackEntry.arguments?.getInt("pos")?.takeIf {it>=0}
            when (tipo) {
                "FRUTOS" -> {
                    val viewModel: FrutosViewModel = viewModel(
                        factory = FrutosViewModelFactory(repository, censoId, lineaId, initialPosition)
                    )
                    FrutosScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                "PRODUCCION" -> {
                    val viewModel: ProduccionViewModel = viewModel(
                        factory = ProduccionViewModelFactory(repository, censoId, lineaId)
                    )
                    ProduccionScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                "FLORACION" -> {
                    val viewModel: FloracionViewModel = viewModel(
                        factory = FloracionViewModelFactory(repository, censoId, lineaId, initialPosition, CountDirection.reversed(LocalContext.current,censoId,lineaId))
                    )
                    FloracionScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

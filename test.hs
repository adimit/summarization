module Main where

import System.Directory (getDirectoryContents, doesFileExist)
import qualified Data.Map.Lazy as M
import Data.List (foldl')
import Control.Monad (filterM)

testDirectory :: String
testDirectory = "test"

tokenize :: String -> [String]
tokenize = words . concatMap adjustPunct
  where adjustPunct x | x `elem` "?!.," = ' ':x:[' ']
        adjustPunct '\'' = " '"
        adjustPunct x = [x]

main :: IO ()
main = do
  files <- getDirectoryContents testDirectory
           >>= filterM doesFileExist . map ((testDirectory ++ "/")++)
           >>= mapM readFile
  let bigMap = foldl' (\m word -> M.insertWith (+) word 1 m)  M.empty
             . tokenize
             . concat
             $ files
      totalObs = sum . map snd $ M.toList bigMap
  putStrLn $ "Total terms: " ++ show (M.size bigMap) ++ ", total obs: " ++ show totalObs
  print $ map fst $ M.toList bigMap

{-# LANGUAGE NamedFieldPuns #-}
module Main where

import Data.Maybe (mapMaybe)
import Control.Applicative ((<$>),(<*>), pure)
import System.Directory (getDirectoryContents, doesFileExist, doesDirectoryExist)
import qualified Data.Map.Lazy as M
import Data.List (foldl')
import Control.Monad (liftM)
import Text.Printf
import System.FilePath ((</>))

data Document = Document { file :: FilePath, text :: String, terms :: [Term] }
data Term = Term String Double

instance Show Document where
  show Document {file, text, terms} = "Document: " ++ file ++ "\n" ++ text ++ "\n++++++++++++Terms+++++++++\n" ++ show terms

instance Show Term where
  show (Term surface tfidf) = surface ++ " (" ++ printf "%.4f" tfidf ++ ")"

type TermFrequencies = M.Map String Int

testDirectory :: String
testDirectory = "test"

tokenize :: String -> [String]
tokenize = words . concatMap adjustPunct
  where adjustPunct x | x `elem` "?!.," = ' ':x:" "
        adjustPunct '\'' = " '"
        adjustPunct x = [x]

scoreDocument :: Int -> TermFrequencies -> TermFrequencies -> String -> FilePath -> Document
scoreDocument n collectionFreqs docFreqs documentContent filePath = Document filePath documentContent terms
  where terms = mapMaybe (uncurry $ computeTFIDF n collectionFreqs) . M.toList $ docFreqs

computeTFIDF :: Int -> TermFrequencies -> String -> Int -> Maybe Term
computeTFIDF n collectionFreqs term termfreq = Term <$> pure term <*>
                                               ((*) <$> pure (fromIntegral termfreq) <*> (logBase 10 <$> idf))
  where df = M.lookup term collectionFreqs
        idf = (/) <$> pure (fromIntegral n) <*> (fromIntegral <$> df)

getFileOrDirectoryRecursively :: FilePath -> IO [FilePath]
getFileOrDirectoryRecursively fp = do
  fileExists <- doesFileExist fp
  if fileExists
    then return [fp]
    else do
    isDirectory <- doesDirectoryExist fp
    if isDirectory
      then liftM (filter (not . (`elem` [".", ".."]))) (getDirectoryContents fp)
           >>= fmap concat . mapM (getFileOrDirectoryRecursively . (fp </>))
      else return []

main :: IO ()
main = do
  files <- getFileOrDirectoryRecursively testDirectory
  fileContents <- mapM readFile files
  let n = length files
      documentFreqs = map (foldl' (\m word -> M.insertWith (+) word 1 m) M.empty . tokenize) fileContents
      collectionFreqs = foldl' (M.unionWith (+)) M.empty documentFreqs
      collectionFreqs' = foldl' (\colFreqs docFreqs -> foldl' (\cf srf -> M.insertWith (+) srf 1 cf) colFreqs (map fst . M.toList $ docFreqs)) M.empty documentFreqs
      documentScorer = scoreDocument n collectionFreqs'
      scoredDocuments = zipWith3 documentScorer documentFreqs fileContents files
      totalObs = sum . map snd $ M.toList collectionFreqs
  print collectionFreqs'
  putStrLn $ "Total terms: " ++ show (M.size collectionFreqs)
        ++ ", total obs: " ++ show totalObs
  mapM_ print scoredDocuments

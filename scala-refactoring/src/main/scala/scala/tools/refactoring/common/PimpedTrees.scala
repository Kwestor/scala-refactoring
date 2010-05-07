package scala.tools.refactoring
package common

import tools.nsc.io.AbstractFile
import tools.nsc.util.RangePosition
import tools.nsc.symtab.{Flags, Names, Symbols}
import reflect.ClassManifest.fromClass
import scala.tools.nsc.ast.parser.Tokens

/**
 * A bunch of implicit conversions for ASTs and other helper
 * functions that work on trees. Users of the trait need to
 * provide the means to access a file's corresponding tree.
 * 
 * */
trait PimpedTrees {
  
  val global: scala.tools.nsc.interactive.Global
  import global._
  
  /**
   * Returns the tree that is contained in this file.
   * Typically done with global.unitOfFile.
   * */
  def treeForFile(file: AbstractFile): Option[Tree]
    
  def cuRoot(p: global.Position) = treeForFile(p.source.file)
  
  /**
   * Extract the modifiers with their position from a Modifiers
   * object and returns them in the order they appear in the
   * source code.
   * */
  object ModifiersTree {
    def unapply(m: global.Modifiers) = {
      Some(m.positions.toList map {
        case (flag, NoPosition) => 
          ModifierTree(flag)
        case (flag, pos) =>
          ModifierTree(flag) setPos (pos withEnd (pos.end + 1))
      })
    }
  }
  
  implicit def importTreeSelectorsExtractor(t: Import) = new {
    // work around for https://lampsvn.epfl.ch/trac/scala/ticket/3392
    def Selectors(ss: List[ImportSelector] = t.selectors) = ss map { imp: ImportSelector =>
    
      val name = NameTree(imp.name) setPos new RangePosition(t.pos.source, imp.namePos,   imp.namePos,   imp.namePos   + imp.name.length)
      
      if(imp.renamePos < 0 || imp.name == imp.rename) {
        ImportSelectorTree(
          name, 
          EmptyTree,
          name.pos)
      } else {
        val rename = NameTree(imp.rename) setPos new RangePosition(t.pos.source, imp.renamePos, imp.renamePos, imp.renamePos + imp.rename.length) 
        ImportSelectorTree(
          name, 
          rename,
          name.pos withPoint rename.pos.start withEnd rename.pos.end)
      }
    }
    
    object Selectors {
      def unapply(ss: List[ImportSelector]) = {
        Some(Selectors(ss))
      }
    }
  }
  
  implicit def nameTreeExtractor(t: Tree) = new {
    object Name {
      def unapply(name: Name) = {
        Some(NameTree(name) setPos pimpPositions(t).namePosition)
      }
    }
  }
  
  /**
   * Represent a name as a tree, including its position.
   * */
  case class NameTree(name: Name) extends Tree {
    def nameString = name.toString.trim
  }
  
  /**
   * Represent a modifier as a tree, including its position.
   * */
  case class ModifierTree(flag: Long) extends Tree {
    
    import Flags._
    
    def nameString = flag match {
      case 0            => ""
      case TRAIT        => "trait"
      case METHOD       => "def"
      case FINAL        => "final"
      case IMPLICIT     => "implicit"
      case PRIVATE      => "private"
      case PROTECTED    => "protected"
      case SEALED       => "sealed"
      case OVERRIDE     => "override"
      case CASE         => "case"
      case ABSTRACT     => "abstract"
      case PARAM        => ""
      case LAZY         => "lazy"
      case Tokens.VAL   => "val"
      case Tokens.VAR   => "var"
      case Tokens.TYPE  => "type"
      case Tokens.DEF   => "def"
      case _            => "<unknown>: " + flagsToString(flag)
    }
  }
  
  /**
   * Represent an import selector as a tree, including both names as trees.
   * */
  case class ImportSelectorTree(name: NameTree, rename: Tree, override val pos: Position) extends Tree {
    setPos(pos)
  }
  
  /**
   * The call to the super constructor in a class:
   * class A(i: Int) extends B(i)
   *                         ^^^^ 
   * */
  case class SuperConstructorCall(clazz: Tree, args: List[Tree]) extends Tree {
    if(clazz.pos != NoPosition) setPos(clazz.pos withEnd args.lastOption.getOrElse(clazz).pos.end)
  }
  
  /**
   * Representation of self type annotations:
   *   self: A with B =>
   *   ^^^^^^^^^^^^^^
   * */
  case class SelfTypeTree(name: NameTree, types: List[Tree]) extends Tree
  
  implicit def additionalTemplateMethods(t: Template) = new {
    def constructorParameters = t.body.filter {
      case ValDef(mods, _, _, _) => mods.hasFlag(Flags.CASEACCESSOR) || mods.hasFlag(Flags.PARAMACCESSOR) || mods.hasFlag(Flags.PARAM) 
      case _ => false
    }
  }
  
  object TemplateTree {
    def unapply(t: Tree) = t match {
      case tpl: Template => 
      
        def empty(t: Tree) = t == EmptyTree || t == emptyValDef
        
        val classParams = tpl.constructorParameters
            
        val restBody = tpl.body -- classParams
        
        val(earlyBody, _) = restBody.filter(_.pos.isRange).partition((t: Tree) => tpl.parents.exists(t.pos precedes _.pos))
                
        val body = (restBody filterNot (earlyBody contains)).filter(t => t.pos.isRange || t.pos == NoPosition).filterNot(empty)
        
        val superCalls = tpl.parents filterNot empty map { superClass =>
        
          val superArgs = earlyBody collect {
            case DefDef(_, _, _, _, _, Block(Apply(_, args) :: _, _)) if args.exists(superClass.pos precedes _.pos) => args
          } flatten
        
          SuperConstructorCall(superClass, superArgs)
        }
        
        val self = if(empty(tpl.self)) EmptyTree else {
          
          val source = tpl.self.pos.source.content.slice(tpl.self.pos.point, tpl.self.pos.end) mkString // XXX remove comments
          
          def extractExactPositionsOfAllTypes(typ: Type): List[NameTree] = typ match {
            case RefinedType(_ :: parents, _) =>
              parents flatMap extractExactPositionsOfAllTypes
            case TypeRef(_, sym, _) =>
              val thisName = sym.name.toString
              val start = tpl.self.pos.point + source.indexOf(thisName)
              val end = start + thisName.length
              List(NameTree(sym.name) setPos (tpl.self.pos withStart start withEnd end))
            case _ => Nil
          }
          
          val selfTypes = extractExactPositionsOfAllTypes(tpl.self.tpt.tpe)
          val namePos = {
            val p = tpl.self.pos
            p withEnd (if(p.start == p.point) p.end else p.point)
          }
          
          SelfTypeTree(NameTree(tpl.self.name) setPos namePos, selfTypes) setPos tpl.self.pos
        }

        Some((classParams, Nil: List[Tree] /*early body*/, superCalls, self, body))
      
      case _ => 
        None
    }
  }
  
  /**
   * Add some methods to Tree that make it easier to compare
   * Trees by position and to extract the position of a tree's
   * name, which is tricky for Selects.
   * */
  implicit def pimpPositions(t: Tree) = new {
    def samePos(p: Position): Boolean = t.pos.sameRange(p) && t.pos.source == p.source
    def samePos(o: Tree)    : Boolean = samePos(o.pos)
    def sameTree(o: Tree)   : Boolean = samePos(o.pos) && fromClass(o.getClass).equals(fromClass(t.getClass))
    def namePosition: Position = t match {
      case t: ModuleDef   => t.pos withStart t.pos.point withEnd (t.pos.point + t.name.toString.trim.length)
      case t: ClassDef    => t.pos withStart t.pos.point withEnd (t.pos.point + t.name.toString.trim.length)
      case t: ValOrDefDef =>
        
        val name = t.name.toString.trim
        
        /* In general, the position of the name starts from t.pos.point and is as long as the trimmed name.
         * But if we have a val in a function: 
         *   ((parameter: Int) => ..)
         *     ^^^^^^^^^^^^^^
         * then the position of the name starts from t.pos.start. To fix this, we extract the source code and
         * check where the parameter actually starts.
         * */
        lazy val src = t.pos.source.content.slice(t.pos.start, t.pos.point).mkString("")
        
        val pos = if(t.pos.point - t.pos.start == name.length && src == name) 
          t.pos withEnd t.pos.point
        else 
          t.pos withStart t.pos.point withEnd (t.pos.point + name.length)
        
        if(t.mods.isSynthetic && t.pos.isTransparent) 
          pos.makeTransparent
        else
          pos
          
      case t @ Select(qualifier, selector) => 
      
        if (qualifier.pos.isRange && qualifier.pos.start > t.pos.start) /* e.g. !true */ {
          t.pos withEnd qualifier.pos.start
        } else if (qualifier.pos.isRange && t.symbol != NoSymbol) {
          t.pos withStart (t.pos.end - t.symbol.nameString.length)
        } else if (qualifier.pos.isRange) {
          t.pos withStart (t.pos.point.max(qualifier.pos.end + 1))
        } else if (qualifier.pos == NoPosition) {
          t.pos
        } else {
          throw new Exception("Unreachable")
        }
        
      case _ => throw new Exception("uhoh")
    }
  }
  
  implicit def nameForIdentTrees(t: Ident) = new {
    def nameString = {
      if(t.name.toString == "<empty>")
        ""
      else if (t.symbol.isSynthetic && t.name.toString.contains("$"))
        "_"
      else if (t.symbol.isSynthetic)
        ""
      else t.name.toString
    }
  }
  
  /**
   * Unify the children of a Block tree and sort them 
   * in the same order they appear in the source code.
   * */
  implicit def blockToBlockWithOrderedChildren(t: Block) = new {
    def body: List[Tree] = if(t.expr.pos.isRange && t.stats.size > 0 && (t.expr.pos precedes t.stats.head.pos))
      t.expr :: t.stats
    else
      t.stats ::: t.expr :: Nil
  }
  
  /**
   * Make a Tree aware of its parent and siblings. Note
   * that these are expensive operations because they
   * traverse the whole compilation unit.
   * */
  implicit def treeToPimpedTree(t: Tree) = new {
    def childTrees: List[Tree] = children(t)
    def originalParent = cuRoot(t.pos) flatMap { root =>
    
      def find(root: Tree): Option[Tree] = {
        val cs = children(root)
        
        if(cs.exists(_ sameTree t))
          Some(root)
        else
          cs.flatMap(find).lastOption
      }
      find(root)
    }
    def originalLeftSibling  = findSibling(originalParent, 1, 0)
    def originalRightSibling = findSibling(originalParent, 0, 1)
    private def findSibling(parent: Option[Tree], compareIndex: Int, returnIndex: Int) = parent flatMap 
      (children(_) filter (_.pos.isRange) sliding 2 find (_ lift compareIndex map (_ samePos t) getOrElse false) flatMap (_ lift returnIndex))
  }
  
  /**
   * Given a Position, returns the tree in that compilation
   * unit that inhabits that position.
   * */
  def findOriginalTreeFromPosition(p: Position): Option[List[Tree]] = {
    def find(t: Tree): List[Tree] = {
      (if(t samePos p)
        t :: Nil
      else 
        Nil) ::: children(t).map(find).flatten
    }
    
    cuRoot(p) map find
  }

  /**
   * Find a tree by its position and make sure that the trees
   * or of the same type. This is necessary because some trees
   * have the same position, for example, a compilation unit
   * without an explicit package and just a single top level
   * class, then the package and the class will have the same
   * position.
   * 
   * If multiple trees are candidates, then take the last one, 
   * because it is likely more specific.
   * */
  def findOriginalTree(t: Tree) = findOriginalTreeFromPosition(t.pos) flatMap (_ filter (_ sameTree t ) lastOption)
  
  /**
   * Returns all children that have a representation in the source code.
   * This includes Name and Modifier trees and excludes everything that
   * has no Position or is an EmptyTree.
   * */
  def children(t: Tree) = (t match {
    
    case t @ PackageDef(pid, stats) => 
      pid :: stats
    
    case t @ ClassDef(ModifiersTree(mods), name, tparams, impl) =>
      mods ::: (NameTree(name) setPos t.namePosition) :: tparams ::: impl :: Nil
      
    case t @ ModuleDef(ModifiersTree(mods), name, impl) =>
      mods ::: (NameTree(name) setPos t.namePosition) :: impl :: Nil
      
    case t @ TemplateTree(params, earlyBody, parents, self, body) =>
      params ::: earlyBody ::: parents ::: self :: body

    case t @ ValDef(ModifiersTree(mods), name, tpt, rhs) =>
      mods ::: (NameTree(name) setPos t.namePosition) :: tpt :: rhs :: Nil
     
    case t @ DefDef(ModifiersTree(mods), name, tparams, vparamss, tpt, rhs) =>
      mods ::: (NameTree(name) setPos t.namePosition) :: tparams ::: vparamss.flatten ::: tpt :: rhs :: Nil
     
    case t: TypeTree =>
      if(t.original != null) t.original :: Nil else Nil
      
    case AppliedTypeTree(tpt, args) =>
      tpt :: args
    
    case _: TypeDef | _: Literal | _: Ident | _: ModifierTree | _: NameTree | _: This => Nil
    
    case t @ Apply(fun, args) =>
      fun :: args
      
    case t @ Select(qualifier, selector) =>
      qualifier :: (NameTree(selector) setPos t.namePosition) :: Nil
      
    case t: Block =>
      t.body
      
    case Return(expr) =>
      expr :: Nil
      
    case New(tpt) =>
      tpt :: Nil
      
    case t @ Import(expr, _) =>
      expr :: t.Selectors()
      
    case ImportSelectorTree(name, rename, _) =>
      name :: rename :: Nil
      
    case SuperConstructorCall(clazz, args) =>
      clazz :: args
      
    case SelfTypeTree(name, types) =>
      name :: types
      
    case TypeApply(fun, args) =>
      fun :: args
      
    case Function(vparams, body) =>
      vparams ::: body :: Nil
      
    case If(cond, thenp, elsep) =>
      cond :: thenp :: elsep :: Nil
    
    case _ => throw new Exception("Unhandled tree: "+ t.getClass.getSimpleName)
     
  }) filterNot (_ == EmptyTree) filterNot (_.pos == NoPosition)
  
}
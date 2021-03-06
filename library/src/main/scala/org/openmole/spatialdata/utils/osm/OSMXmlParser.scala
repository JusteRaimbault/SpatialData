package org.openmole.spatialdata.utils.osm

import java.io._
import java.text.{DateFormat, FieldPosition, ParsePosition, SimpleDateFormat}
import java.util
import java.util.Date

import javax.xml.stream.{XMLInputFactory, XMLStreamConstants, XMLStreamException, XMLStreamReader}
import org.openmole.spatialdata.utils
import org.openmole.spatialdata.utils.osm.OSMXmlParser._
import org.openmole.spatialdata.utils.osm.OSMObject._

import scala.collection.mutable
import scala.util.control.Breaks._


case class OSMXmlParser(
                         root: OSMRoot,
                         allowingMissingVersions: Boolean = true,
                         timestampFormat: OsmXmlTimestampFormat = new OsmXmlTimestampFormat,
                         tagKeyIntern: HashConsing[String] = new HashConsing[String],
                         tagValueIntern: HashConsing[String] = new HashConsing[String],
                         userIntern: HashConsing[String] = new HashConsing[String],
                         roleIntern: HashConsing[String] = new HashConsing[String]
                       ) {

  /**
    *
    * @param xml xml string
    * @return
    * @throws OsmXmlParserException parser exception
    */
  @throws[OsmXmlParserException]
  final def parse(xml: String):OsmXmlParserDelta = parse(new StringReader(xml))

  @throws[OsmXmlParserException]
  def parse(xml: InputStream):OsmXmlParserDelta = try parse(new InputStreamReader(xml, "utf8"))
  catch {
    case e: UnsupportedEncodingException =>
      throw new OsmXmlParserException(e)
  }

  def processParsedNode(node: Node, state: State.Value): Unit = {}

  def processParsedWay(way: Way, state: State.Value): Unit = {}

  def processParsedRelation(relation: Relation, state: State.Value): Unit = {}


  @throws[StreamException]
  def readerFactory(xml: InputStream):  OSMXmlParser.Stream  = try
    readerFactory(new InputStreamReader(xml, "utf8"))
  catch {
    case e: UnsupportedEncodingException =>
      throw new StreamException(e)
  }

  @throws[OsmXmlParserException]
  def parse(xml: Reader): OsmXmlParserDelta = {
    val started = System.currentTimeMillis
    val delta = OsmXmlParserDelta()
    try {
      val xmlr:  OSMXmlParser.Stream  = readerFactory(xml)
      var current: OSMObject = null
      var currentNode: Node = null
      var currentRelation: Relation = null
      var currentWay: Way = null
      var skipCurrentObject = false
      var state: State.Value = State.none
      var eventType: Int = xmlr.next // START_DOCUMENT
      while (!xmlr.isEndDocument(eventType)) {
        breakable {
          if (xmlr.isStartElement(eventType)) {
            if ("create" == xmlr.getLocalName) state = State.create
            else if ("modify" == xmlr.getLocalName) state = State.modify
            else if ("delete" == xmlr.getLocalName) state = State.delete
            else if ("node" == xmlr.getLocalName) {
              val identity = xmlr.getAttributeValue(null, "id").toLong
              if ((state == State.none) || (state == State.create)) {
                currentNode = root.getNode(identity)
                if (currentNode != null && currentNode.isLoaded && currentNode.getVersion != null) {
                  val version = Integer.valueOf(xmlr.getAttributeValue(null, "version"))
                  if (version <= currentNode.getVersion) {
                    skipCurrentObject = true
                    break //was:continue
                    //              } else if (version > currentNode.getVersion() + 1) {
                    //                throw new OsmXmlParserException("Inconsistency, too great version found during create node.");
                  }
                  else throw new OsmXmlParserException("Inconsistency, node " + identity + " already exists.")
                }
                if (currentNode == null) currentNode = new Node(identity)
                currentNode.setLatitude(xmlr.getAttributeValue(null, "lat").toDouble)
                currentNode.setLongitude(xmlr.getAttributeValue(null, "lon").toDouble)
                parseObjectAttributes(xmlr, currentNode, "id", "lat", "lon")
                currentNode.setLoaded(true)
                current = currentNode
                delta.createdNodes.add(currentNode)
                root.add(currentNode)
              }
              else if (state == State.modify) {
                currentNode = root.getNode(identity)
                if (currentNode == null) throw new OsmXmlParserException("Inconsistency, node " + identity + " does not exists.")
                val version = Integer.valueOf(xmlr.getAttributeValue(null, "version"))
                if (version <= currentNode.getVersion) {
                  utils.log("Inconsistency, old version detected during modify node.")
                  skipCurrentObject = true
                  break //was:continue
                }
                else if (version > currentNode.getVersion + 1 && !allowingMissingVersions) throw new OsmXmlParserException("Inconsistency, version " + version + " too great to modify node " + currentNode.id + " with version " + currentNode.getVersion)
                else if (version == currentNode.getVersion) throw new OsmXmlParserException("Inconsistency, found same version in new data during modify node.")
                currentNode.setTags(null)
                currentNode.setAttributes(null)
                currentNode.setLatitude(xmlr.getAttributeValue(null, "lat").toDouble)
                currentNode.setLongitude(xmlr.getAttributeValue(null, "lon").toDouble)
                parseObjectAttributes(xmlr, currentNode, "id", "lat", "lon")
                current = currentNode
                delta.modifiedNodes.add(currentNode)
                root.add(currentNode)
              }
              else if (state == State.delete) {
                val nodeToRemove = root.getNode(identity)
                if (nodeToRemove == null) {
                  utils.log("Inconsistency, node " + identity + " does not exists.")
                  skipCurrentObject = true
                  break //was:continue
                }
                val version = Integer.valueOf(xmlr.getAttributeValue(null, "version"))
                if (version < nodeToRemove.getVersion) {
                  utils.log("Inconsistency, old version detected during delete node.")
                  skipCurrentObject = true
                  break //was:continue
                }
                else if (version > nodeToRemove.getVersion + 1 && !allowingMissingVersions) throw new OsmXmlParserException("Inconsistency, too great version found during delete node.")
                root.remove(nodeToRemove)
                delta.deletedNodes.add(nodeToRemove)
              }
            }
            else if ("way" == xmlr.getLocalName) {
              val identity = xmlr.getAttributeValue(null, "id").toLong
              if ((state == State.none) || (state == State.create)) {
                currentWay = root.getWay(identity)
                if (currentWay != null && currentWay.isLoaded && currentWay.getVersion != null) {
                  val version = Integer.valueOf(xmlr.getAttributeValue(null, "version"))
                  if (version <= currentWay.getVersion) {
                    utils.log("Inconsistency, old version detected during create way.")
                    skipCurrentObject = true
                    break //was:continue
                    //              } else if (version > currentWay.getVersion() + 1) {
                    //                throw new OsmXmlParserException("Inconsistency, too great version found during create way.");
                  }
                  else throw new OsmXmlParserException("Inconsistency, way " + identity + " already exists.")
                }
                if (currentWay == null) currentWay = new Way(identity)

                parseObjectAttributes(xmlr, currentWay, "id")
                currentWay.setLoaded(true)
                current = currentWay
                delta.createdWays.add(currentWay)
                root.add(currentWay)// added line
              }
              else if (state == State.modify) {
                currentWay = root.getWay(identity)
                if (currentWay == null) throw new OsmXmlParserException("Inconsistency, way " + identity + " does not exists.")
                val version = Integer.valueOf(xmlr.getAttributeValue(null, "version"))
                if (version <= currentWay.getVersion) {
                  println("Inconsistency, old version detected during modify way.")
                  skipCurrentObject = true
                  break //was:continue
                }
                else if (version > currentWay.getVersion + 1 && !allowingMissingVersions) throw new OsmXmlParserException("Inconsistency, found too great version in new data during modify way.")
                else if (version == currentWay.getVersion) throw new OsmXmlParserException("Inconsistency, found same version in new data during modify way.")
                //                currentWay.setTags(null)
                currentWay.setAttributes(null)
                if (currentWay.nodes != null) {
                  for (node <- currentWay.nodes) {
                    node.getWaysMemberships.remove(node.getWaysMemberships.indexOf(currentWay))
                    root.add(node)
                  }
                }
                currentWay.nodes = new mutable.ArrayBuffer[Node]
                parseObjectAttributes(xmlr, currentWay, "id")
                current = currentWay
                delta.modifiedWays.add(currentWay)
              }
              else if (state == State.delete) {
                val wayToRemove = root.getWay(identity)
                if (wayToRemove == null) {
                  println("Inconsistency, way \" + identity + \" does not exists.")
                  skipCurrentObject = true
                  break //was:continue
                }
                val version = Integer.valueOf(xmlr.getAttributeValue(null, "version"))
                if (version < wayToRemove.getVersion) {
                  println("Inconsistency, old version detected during delete way.")
                  skipCurrentObject = true
                  break //was:continue
                }
                else if (version > wayToRemove.getVersion + 1 && !allowingMissingVersions) throw new OsmXmlParserException("Inconsistency, too great way version found during delete way.")
                root.remove(wayToRemove)
                delta.deletedWays.add(wayToRemove)
              }
            }
            else if ("nd" == xmlr.getLocalName) { // a node reference inside of a way
              if (skipCurrentObject) break //was:continue
              //! continue is not supported
              val identity = xmlr.getAttributeValue(null, "ref").toLong
              if ((state == State.none) || (state == State.create) || (state == State.modify)) {
                var node = root.getNode(identity)
                if (node == null) {
                  node = new Node(identity)
                  root.add(node)
                }
                node.addWayMembership(currentWay)
                currentWay.addNode(node)
              }
              else if (state == State.delete) {
                //throw new OsmXmlParserException("Lexical error, delete way should not contain <nd> elements.");
              }
            }
            else if ("relation" == xmlr.getLocalName) {
              // multi polygon, etc
              val identity = xmlr.getAttributeValue(null, "id").toLong
              if ((state == State.none) || (state == State.create)) {
                currentRelation = root.getRelation(identity)
                if (currentRelation != null && currentRelation.isLoaded && currentRelation.getVersion != null) {
                  val version = Integer.valueOf(xmlr.getAttributeValue(null, "version"))
                  if (version <= currentRelation.getVersion) {
                    println("Inconsistency, old version detected during create relation.")
                    skipCurrentObject = true
                    break //was:continue
                    //              } else if (version > currentRelation.getVersion() + 1) {
                    //                throw new OsmXmlParserException("Inconsistency, too great version found during create relation.");
                  }
                  else throw new OsmXmlParserException("Inconsistency, relation " + identity + " already exists.")
                }
                if (currentRelation == null) {
                  currentRelation = new Relation(identity)
                }
                parseObjectAttributes(xmlr, currentRelation, "id")
                currentRelation.setLoaded(true)
                current = currentRelation
                delta.createdRelations.add(currentRelation)
              }
              else if (state == State.modify) {
                currentRelation = root.getRelation(identity)
                if (currentRelation == null) throw new OsmXmlParserException("Inconsistency, relation " + identity + " does not exists.")
                val version = Integer.valueOf(xmlr.getAttributeValue(null, "version"))
                if (version < currentRelation.getVersion) {
                  utils.log("Inconsistency, old version detected during modify relation.")
                  skipCurrentObject = true
                  break //was:continue
                }
                else if (version > currentRelation.getVersion + 1 && !allowingMissingVersions) throw new OsmXmlParserException("Inconsistency, too great version found during modify relation.")
                else if (version == currentRelation.getVersion) throw new OsmXmlParserException("Inconsistency, same version found during modify relation.")
                if (currentRelation.members != null) {

                  for (member <- currentRelation.members) {
                    member.getOsmObject.getRelationMemberships.remove(member.getOsmObject.getRelationMemberships.indexOf(member))
                    if (member.getOsmObject.getRelationMemberships.isEmpty) member.getOsmObject.setRelationMemberships(null)
                  }
                  currentRelation.members = new mutable.ArrayBuffer[RelationMembership]
                }
                currentRelation.setAttributes(null)
                currentRelation.setTags(null)
                current = currentRelation
                parseObjectAttributes(xmlr, currentRelation, "id")
                delta.modifiedRelations.add(currentRelation)
              }
              else if (state == State.delete) {
                val relationToRemove = root.getRelation(identity)
                if (relationToRemove == null) {
                  utils.log("Inconsistency, relation \" + identity + \" does not exist.")
                  skipCurrentObject = true
                  break //was:continue
                }
                val version = Integer.valueOf(xmlr.getAttributeValue(null, "version"))
                if (version < relationToRemove.getVersion) {
                  utils.log("Inconsistency, old version detected during delete relation.")
                  skipCurrentObject = true
                  break //was:continue
                }
                else if (version > relationToRemove.getVersion + 1 && !allowingMissingVersions) throw new OsmXmlParserException("Inconsistency, too great version found during delete relation.")
                if (relationToRemove.members != null) {
                  for (member <- relationToRemove.members) {
                    member.getOsmObject.getRelationMemberships.remove(member.getOsmObject.getRelationMemberships.indexOf(member))
                    if (member.getOsmObject.getRelationMemberships.isEmpty) member.getOsmObject.setRelationMemberships(null)
                  }
                  relationToRemove.members = new mutable.ArrayBuffer[RelationMembership]
                }
                root.remove(relationToRemove)
                delta.deletedRelations.add(relationToRemove)
              }
            }
            else if ("member" == xmlr.getLocalName) { // multi polygon member
              if (skipCurrentObject) break //was:continue
              if ((state == State.none) || (state == State.create) || (state == State.modify)) {
                val member = new RelationMembership
                member.setRelation(currentRelation)
                member.setRole(roleIntern.intern(xmlr.getAttributeValue(null, "role")))
                val identity = xmlr.getAttributeValue(null, "ref").toLong
                val `type` = xmlr.getAttributeValue(null, "type")
                if ("way" == `type`) {
                  var way = root.getWay(identity)
                  if (way == null) {
                    way = new Way(identity)
                    root.add(way)
                  }
                  member.setOsmObject(way)
                }
                else if ("node" == `type`) {
                  var node = root.getNode(identity)
                  if (node == null) {
                    node = new Node(identity)
                    root.add(node)
                  }
                  member.setOsmObject(node)
                }
                else if ("relation" == `type`) {
                  var relation = root.getRelation(identity)
                  if (relation == null) {
                    relation = new Relation(identity)
                    root.add(relation)
                  }
                  member.setOsmObject(relation)
                }
                else throw new RuntimeException("Unsupported relation member type: " + `type`)
                member.getOsmObject.addRelationMembership(member)
                currentRelation.addMember(member)
              }
              else if (state == State.delete) {
                //throw new OsmXmlParserException("Lexical error, delete relation should not contain <member> elements.");
              }
            }
            else if ("tag" == xmlr.getLocalName) { // tag of any object type
              if (skipCurrentObject) break //was:continue
              if ((state == State.none) || (state == State.create) || (state == State.modify)) {
                val key = tagKeyIntern.intern(xmlr.getAttributeValue(null, "k"))
                val value = tagValueIntern.intern(xmlr.getAttributeValue(null, "v"))
                current.setTag(key, value)
              }
              else if (state == State.delete) {
                //throw new OsmXmlParserException("Lexical error, delete object should not contain <tag> elements.");
              }
            }
            else if (xmlr.isEndElement(eventType)) if ("create" == xmlr.getLocalName) state = State.none
            else if ("modify" == xmlr.getLocalName) state = State.none
            else if ("delete" == xmlr.getLocalName) state = State.none
            else if ("node" == xmlr.getLocalName) {
              if ((state == State.none) || (state == State.create) || (state == State.modify)) root.add(currentNode)
              processParsedNode(currentNode, state)
              currentNode = null
              current = null
              skipCurrentObject = false
            }
            else if ("way" == xmlr.getLocalName) {
              if ((state == State.none) || (state == State.create) || (state == State.modify)) root.add(currentWay)
              processParsedWay(currentWay, state)
              currentWay = null
              current = null
              skipCurrentObject = false
            }
            else if ("relation" == xmlr.getLocalName) {
              if ((state == State.none) || (state == State.create) || (state == State.modify)) root.add(currentRelation)
              processParsedRelation(currentRelation, state)
              currentRelation = null
              current = null
              skipCurrentObject = false
            }
            else {
              // what not
            }
          }
        }
        eventType = xmlr.next
      }
      xmlr.close()
    } catch {
      case ioe: StreamException =>
        throw new OsmXmlParserException(ioe)
    }
    val timespent = System.currentTimeMillis - started
    utils.log(s"time spent = $timespent ms")
    delta
  }

  @throws[StreamException]
  private def parseObjectAttributes(xmlr: OSMXmlParser.Stream, osmObject: OSMObject, parsedAttributes: String*): Unit = {
    var attributeIndex = 0
    while ( {
      attributeIndex < xmlr.getAttributeCount
    }) {
      val key = xmlr.getAttributeLocalName(attributeIndex)
      val value = xmlr.getAttributeValue(attributeIndex)
      if ("version" == key) osmObject.setVersion(Integer.valueOf(value))
      else if ("changeset" == key) osmObject.setChangeset(value.toLong)
      else if ("uid" == key) osmObject.setUid(value.toLong)
      else if ("user" == key) osmObject.setUser(userIntern.intern(value))
      else if ("visible" == key) osmObject.setVisible(value.toBoolean)
      else if ("timestamp" == key) try {
        osmObject.setTimestamp(timestampFormat.parse(value).getTime)
      } catch {
        case pe: Exception =>
          throw new RuntimeException(pe)
      }
      else {
        var parsed = false
        breakable {
          for (parsedAttribute <- parsedAttributes) {
            if (parsedAttribute == key) {
              parsed = true
              break
            }
          }
        }
        if (!parsed) {
          osmObject.setAttribute(key, value)
        }
      }
      {
        attributeIndex += 1; attributeIndex - 1
      }
    }
  }



  private val xmlif = XMLInputFactory.newInstance

  @throws[StreamException]
  def readerFactory(xml: Reader):  OSMXmlParser.Stream = {
    var xmlr: XMLStreamReader = null
    try
      xmlr = xmlif.createXMLStreamReader(xml)
    catch {
      case e: XMLStreamException =>
        throw new StreamException(e)
    }
    new  OSMXmlParser.Stream  {
      @throws[StreamException]
      def getEventType: Int = xmlr.getEventType

      @throws[StreamException]
      def isEndDocument(eventType: Int): Boolean = eventType == XMLStreamConstants.END_DOCUMENT

      @throws[StreamException]
      def next: Int = try
        xmlr.next
      catch {
        case e: XMLStreamException =>
          throw new StreamException(e)
      }

      @throws[StreamException]
      def isStartElement(eventType: Int): Boolean = eventType == XMLStreamConstants.START_ELEMENT

      @throws[StreamException]
      def isEndElement(eventType: Int): Boolean = eventType == XMLStreamConstants.END_ELEMENT

      @throws[StreamException]
      def getLocalName: String = xmlr.getLocalName

      @throws[StreamException]
      def getAttributeValue(what: String, key: String): String = xmlr.getAttributeValue(what, key)

      @throws[StreamException]
      def getAttributeCount: Int = xmlr.getAttributeCount

      @throws[StreamException]
      def getAttributeValue(index: Int): String = xmlr.getAttributeValue(index)

      @throws[StreamException]
      def getAttributeLocalName(index: Int): String = xmlr.getAttributeLocalName(index)

      @throws[StreamException]
      def close(): Unit = {
        try
          xmlr.close()
        catch {
          case e: XMLStreamException =>
            throw new StreamException(e)
        }
      }
    }
  }



}



/**
  * OSM data parser
  */
object OSMXmlParser {


  class HashConsing[T] extends Serializable {
    private val map = new mutable.HashMap[T, T]()

    def intern(obj: T): T = {
      map.get(obj) match {
        case None =>
          map.put(obj, obj)
          obj
        case Some(t) => t
      }
    }
  }

  object State extends Enumeration {
    type State = Value
    val none, create, modify, delete = Value
  }

  /**
    * formats for xml timestamp
    */
  class OsmXmlTimestampFormat extends DateFormat {

    private val format1 = "yyyy-MM-dd'T'HH:mm:ss'Z'"
    private val format2 = "yyyy-MM-dd'T'HH:mm:ss"

    private val implementation1 = new SimpleDateFormat(format1)
    private val implementation2 = new SimpleDateFormat(format2)

    override def format(date: Date, stringBuffer: StringBuffer, fieldPosition: FieldPosition): StringBuffer = implementation1.format(date, stringBuffer, fieldPosition)

    override def parse(s: String, parsePosition: ParsePosition): Date = {
      if (s.length - parsePosition.getIndex == format1.length)
        return implementation1.parse(s, parsePosition)
      implementation2.parse(s, parsePosition)
    }
  }

  case class OsmXmlParserDelta(
                            createdNodes: util.Set[Node] = new util.HashSet[Node],
                            modifiedNodes: util.Set[Node] = new util.HashSet[Node],
                            deletedNodes: util.Set[Node] = new util.HashSet[Node],
                            createdWays: util.Set[Way] = new util.HashSet[Way],
                            modifiedWays: util.Set[Way] = new util.HashSet[Way],
                            deletedWays: util.Set[Way] = new util.HashSet[Way],
                            createdRelations: util.Set[Relation] = new util.HashSet[Relation],
                            modifiedRelations: util.Set[Relation] = new util.HashSet[Relation],
                            deletedRelations: util.Set[Relation] = new util.HashSet[Relation]
                         )

  /**
    * parsing exception
    * @param s message
    * @param throwable exception
    */
  class OsmXmlParserException(s: String, throwable: Throwable) extends Exception(s, throwable) {
    def this(s: String) {
      this(s, null)
    }
    def this(throwable: Throwable) {
      this("", throwable)
    }
  }



  class StreamException(message: String, cause: Throwable) extends Exception(message, cause) {
    def this(message: String) {
      this(message, null)
    }

    def this(cause: Throwable) {
      this("", cause)
    }
  }

  abstract class Stream {
    @throws[StreamException]
    def getEventType: Int

    @throws[StreamException]
    def isEndDocument(eventType: Int): Boolean

    @throws[StreamException]
    def next: Int

    @throws[StreamException]
    def isStartElement(eventType: Int): Boolean

    @throws[StreamException]
    def isEndElement(eventType: Int): Boolean

    @throws[StreamException]
    def getLocalName: String

    @throws[StreamException]
    def getAttributeValue(what: String, key: String): String

    @throws[StreamException]
    def getAttributeCount: Int

    @throws[StreamException]
    def getAttributeValue(index: Int): String

    @throws[StreamException]
    def getAttributeLocalName(index: Int): String

    @throws[StreamException]
    def close(): Unit
  }


}


